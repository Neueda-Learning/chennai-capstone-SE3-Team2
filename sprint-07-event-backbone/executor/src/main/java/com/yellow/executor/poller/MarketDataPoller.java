package com.yellow.executor.poller;

import com.yellow.executor.config.FauxnanceProperties;
import com.yellow.executor.config.PollProperties;
import com.yellow.executor.events.EventEnvelope;
import com.yellow.executor.events.QuotePayload;
import com.yellow.executor.persistence.ExecutionMapper;
import com.yellow.executor.quotes.Quote;
import com.yellow.executor.quotes.QuotaCounter;
import com.yellow.executor.quotes.QuoteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manufactures the price stream Fauxnance does not have.
 *
 * <p>The curriculum promises a real-time feed. Fauxnance serves end-of-day
 * candles and delayed quotes over HTTP, with no WebSocket and no server-sent
 * events. This component turns polling into a stream, and its existence is the
 * reason the Sprint 10 extensions that read prices have anything to consume.
 *
 * <h2>Why it lives inside the executor</h2>
 *
 * The reason is the key. The executor already calls Fauxnance to price every
 * fill. A separate poller would mean a second process holding the same
 * credential, spending the same 2000 requests a day with no idea what the other
 * one had spent, and an argument about which of the two owned the retry policy.
 * One component calls Fauxnance, so one component holds the key and one
 * component divides the budget -- see {@link QuotaCounter}.
 *
 * <p>SHARING A PROCESS IS NOT SHARING A LIFECYCLE, and that is the part teams
 * get wrong. This is not on the order path. It does not start a poll because an
 * order arrived, and the consumer never waits for one to finish. The only thing
 * the two share is the credential and the budget.
 *
 * <h2>One message per symbol, never one per batch</h2>
 *
 * Batching the HTTP call is a quota optimisation and it is correct. Batching
 * the Kafka message would not be: several symbols behind one key land on one
 * partition together, and the per-symbol ordering the contract promises is
 * gone. A consumer would have no way to know it must never see an older AAPL
 * quote after a newer one.
 */
@Component
public class MarketDataPoller {

    private static final Logger log = LoggerFactory.getLogger(MarketDataPoller.class);

    private static final String EVENT_TYPE = "QUOTE";

    /**
     * The contract names the producing COMPONENT, not the container it shipped
     * in. Quotes carry market-poller even though this runs inside the Trade
     * Executor, which keeps a quote distinguishable from an execution event.
     */
    private static final String SOURCE = "market-poller";

    private final ExecutionMapper mapper;
    private final QuoteSource quotes;
    private final QuotaCounter quota;
    private final KafkaTemplate<String, EventEnvelope<QuotePayload>> marketDataTemplate;
    private final PollSchedule schedule;
    private final PollProperties poll;
    private final FauxnanceProperties fauxnance;
    private final Clock clock;

    @Value("${executor.topics.market-data:market-data}")
    private String marketDataTopic;

    public MarketDataPoller(ExecutionMapper mapper,
                            QuoteSource quotes,
                            QuotaCounter quota,
                            KafkaTemplate<String, EventEnvelope<QuotePayload>> marketDataTemplate,
                            PollSchedule schedule,
                            PollProperties poll,
                            FauxnanceProperties fauxnance,
                            Clock clock) {
        this.mapper = mapper;
        this.quotes = quotes;
        this.quota = quota;
        this.marketDataTemplate = marketDataTemplate;
        this.schedule = schedule;
        this.poll = poll;
        this.fauxnance = fauxnance;
        this.clock = clock;
    }

    /**
     * One cycle: read the symbol set, fetch it in batches, publish one message
     * per symbol.
     *
     * <p>NEVER THROWS. A scheduled method that throws is not necessarily
     * rescheduled, and a poller that quietly stopped inside a running container
     * is far harder to notice than one whose container exited -- the health
     * check still passes, the logs go silent, and the first symptom is a
     * consumer wondering why prices stopped an hour ago. So the cycle catches
     * everything, logs it, and lets the next tick try again.
     *
     * @return how many messages were published, for the tests and the logs
     */
    public int pollOnce() {
        try {
            return runCycle();
        } catch (Exception e) {
            log.error("poll cycle failed; the schedule continues and the next tick will retry", e);
            return 0;
        }
    }

    private int runCycle() {
        if (!poll.enabled()) {
            return 0;
        }

        // Only what somebody holds or is waiting on. Polling the whole
        // instrument table would spend the budget on prices nobody reads.
        List<String> symbols = mapper.findSymbolsWorthPolling();
        if (symbols.isEmpty()) {
            log.debug("nothing held and nothing working: no symbols to poll");
            return 0;
        }

        int callsNeeded = schedule.callsPerCycle(symbols.size());
        if (!quota.canAfford(callsNeeded, fauxnance.pollerBudget())) {
            // The reserve exists so that an order a customer is waiting on can
            // still be priced. The poller yields; it is the one whose work can
            // wait until tomorrow.
            log.warn("skipping poll: {} request(s) would exceed the poller's budget of {} "
                            + "({} already spent today). The fill path keeps its reserve.",
                    callsNeeded, fauxnance.pollerBudget(), quota.spentToday());
            return 0;
        }

        int published = 0;
        for (List<String> batch : batches(symbols)) {
            Map<String, Quote> fetched = quotes.quotes(batch);

            for (Map.Entry<String, Quote> entry : fetched.entrySet()) {
                publish(entry.getKey(), entry.getValue());
                published++;
            }

            int missing = batch.size() - fetched.size();
            if (missing > 0) {
                // Expected, and not an error. Our fictional Sprint 3 tickers
                // are not in the Fauxnance registry and never will be.
                log.debug("{} of {} symbols in this batch had no quote", missing, batch.size());
            }
        }

        log.info("poll cycle published {} quote(s) for {} symbol(s) in {} request(s); "
                        + "{} of {} daily requests spent",
                published, symbols.size(), callsNeeded, quota.spentToday(), fauxnance.dailyQuota());

        return published;
    }

    /**
     * One message per symbol, keyed by the symbol so that every quote for one
     * instrument lands on one partition and stays in order relative to the
     * others for that instrument.
     */
    private void publish(String symbol, Quote quote) {
        Instant now = Instant.now(clock);

        QuotePayload payload = new QuotePayload(
                quote.symbol(),
                quote.price(),
                quote.bid(),
                quote.ask(),
                quote.spreadBps(),
                quote.currency(),
                quote.change(),
                quote.changePercent(),
                quote.previousClose(),
                quote.marketState(),
                quote.stale(),
                // The OBSERVATION time from Fauxnance, not the poll time. The
                // envelope carries when we published; this carries when the
                // price was true.
                quote.asOf());

        EventEnvelope<QuotePayload> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                now,
                SOURCE,
                1,
                payload);

        marketDataTemplate.send(marketDataTopic, symbol, envelope);
    }

    /** Chunks of at most the batch size: 26 symbols is a 400, not a truncation. */
    private List<List<String>> batches(List<String> symbols) {
        int size = poll.batchSize();
        return java.util.stream.IntStream
                .iterate(0, start -> start < symbols.size(), start -> start + size)
                .mapToObj(start -> symbols.subList(start, Math.min(start + size, symbols.size())))
                .toList();
    }
}
