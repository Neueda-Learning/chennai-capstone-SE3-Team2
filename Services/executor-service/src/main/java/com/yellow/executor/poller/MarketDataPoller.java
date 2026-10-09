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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manufactures the price stream Fauxnance does not have. The curriculum
 * promises a real-time feed.
 */
@Component
public class MarketDataPoller {

    private static final Logger log = LoggerFactory.getLogger(MarketDataPoller.class);

    private static final String EVENT_TYPE = "QUOTE";

    /** The contract names the producing COMPONENT, not the container it shipped in. */
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
                            // Named, because this context holds three
                            // KafkaTemplate beans and generic-type
                            @Qualifier("marketDataTemplate")
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
     * per symbol. NEVER THROWS.
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
            // The reserve exists so that an order a customer is waiting on
            // can still be priced. The poller yields; it is the one whose
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
                // The OBSERVATION time from Fauxnance, not the poll time.
                // The envelope carries when we published; this carries when
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
