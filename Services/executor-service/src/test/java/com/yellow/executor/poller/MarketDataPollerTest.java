package com.yellow.executor.poller;

import com.yellow.executor.config.FauxnanceProperties;
import com.yellow.executor.config.PollProperties;
import com.yellow.executor.events.EventEnvelope;
import com.yellow.executor.events.QuotePayload;
import com.yellow.executor.persistence.ExecutionMapper;
import com.yellow.executor.quotes.Quote;
import com.yellow.executor.quotes.QuotaCounter;
import com.yellow.executor.quotes.QuoteSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The poller's contract with the rest of the platform. The load-bearing
 * assertion in here is that N symbols produce N messages, each keyed by its
 * own symbol.
 */
class MarketDataPollerTest {

    private static final Instant NOW = Instant.parse("2026-09-18T09:15:00Z");
    private static final Instant OBSERVED = Instant.parse("2026-09-18T09:14:58Z");

    private ExecutionMapper mapper;
    private QuoteSource quotes;
    private QuotaCounter quota;
    private KafkaTemplate<String, EventEnvelope<QuotePayload>> template;
    private MarketDataPoller poller;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void wire() {
        mapper = mock(ExecutionMapper.class);
        quotes = mock(QuoteSource.class);
        template = mock(KafkaTemplate.class);

        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        quota = new QuotaCounter(fixed);

        PollProperties poll = new PollProperties(60, 60, 25, true);
        FauxnanceProperties fauxnance =
                new FauxnanceProperties("http://x", "k", null, 3, null, null, 2000, 200);

        poller = new MarketDataPoller(mapper, quotes, quota, template,
                new PollSchedule(poll, fauxnance), poll, fauxnance, fixed);
        ReflectionTestUtils.setField(poller, "marketDataTopic", "market-data");
    }

    // --------------------------------------------------------- the batching

    @Test
    @DisplayName("25 symbols are fetched in ONE request")
    void twentyFiveSymbolsIsOneRequest() {
        givenSymbols(symbols(25));
        when(quotes.quotes(anyList())).thenAnswer(inv -> quotesFor(inv.getArgument(0)));

        poller.pollOnce();

        // One HTTP call, whatever the symbol count up to the cap. That is the
        // whole quota optimisation.
        verify(quotes, times(1)).quotes(anyList());
    }

    @Test
    @DisplayName("26 symbols become two requests, 25 and 1, never one oversized call")
    void twentySixSymbolsIsTwoRequests() {
        givenSymbols(symbols(26));
        when(quotes.quotes(anyList())).thenAnswer(inv -> quotesFor(inv.getArgument(0)));

        poller.pollOnce();

        ArgumentCaptor<List<String>> batches = ArgumentCaptor.forClass(List.class);
        verify(quotes, times(2)).quotes(batches.capture());

        // The API returns 400 for 26, so chunking is not an optimisation here,
        // it is the difference between working and not.
        assertThat(batches.getAllValues().get(0).size(), is(25));
        assertThat(batches.getAllValues().get(1).size(), is(1));
    }

    // ------------------------------------------------ one message per symbol

    @Test
    @DisplayName("each quote is published as its own message, keyed by its own symbol")
    void onePublishPerSymbolKeyedBySymbol() {
        givenSymbols(List.of("MRF.NS", "ITC.NS", "TCS.NS"));
        when(quotes.quotes(anyList())).thenAnswer(inv -> quotesFor(inv.getArgument(0)));

        int published = poller.pollOnce();

        assertThat(published, is(3));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EventEnvelope<QuotePayload>> sent =
                ArgumentCaptor.forClass(EventEnvelope.class);
        verify(template, times(3)).send(anyString(), keys.capture(), sent.capture());

        // Three messages, not one carrying three quotes.
        assertThat(keys.getAllValues(), contains("MRF.NS", "ITC.NS", "TCS.NS"));
        // And the key is the symbol, so one instrument's quotes stay on one
        // partition and stay in order relative to each other.
        assertThat(sent.getAllValues().stream()
                        .map(e -> e.payload().symbol()).toList(),
                contains("MRF.NS", "ITC.NS", "TCS.NS"));
    }

    @Test
    @DisplayName("a batch of 25 publishes 25 messages, never 1")
    void aBatchIsNotAMessage() {
        givenSymbols(symbols(25));
        when(quotes.quotes(anyList())).thenAnswer(inv -> quotesFor(inv.getArgument(0)));

        assertThat(poller.pollOnce(), is(25));
        verify(template, times(25)).send(anyString(), anyString(), any());
    }

    // ------------------------------------------------------- the envelope

    @Test
    @DisplayName("the envelope says market-poller, not trade-executor")
    void sourceNamesTheComponentNotTheContainer() {
        givenSymbols(List.of("MRF.NS"));
        when(quotes.quotes(anyList())).thenAnswer(inv -> quotesFor(inv.getArgument(0)));

        poller.pollOnce();

        // The contract names the producing COMPONENT.
        assertThat(captured().source(), is("market-poller"));
        assertThat(captured().eventType(), is("QUOTE"));
        assertThat(captured().schemaVersion(), is(1));
    }

    @Test
    @DisplayName("eventTime is when we published; quoteAsOf is when the price was observed")
    void thePairOfTimestampsIsNotTheSame() {
        givenSymbols(List.of("MRF.NS"));
        when(quotes.quotes(anyList())).thenAnswer(inv -> quotesFor(inv.getArgument(0)));

        poller.pollOnce();

        assertThat(captured().eventTime(), is(NOW));
        // Fauxnance serves delayed quotes, so a strategy acting on eventTime
        // is acting on a price older than it thinks.
        assertThat(captured().payload().quoteAsOf(), is(OBSERVED));
    }

    @Test
    @DisplayName("the payload carries the price fields a consumer needs, including stale")
    void payloadCarriesTheContractFields() {
        givenSymbols(List.of("MRF.NS"));
        when(quotes.quotes(anyList())).thenAnswer(inv -> quotesFor(inv.getArgument(0)));

        poller.pollOnce();

        QuotePayload payload = captured().payload();
        assertThat(payload.bid(), comparesEqualTo(new BigDecimal("125083.94")));
        assertThat(payload.ask(), comparesEqualTo(new BigDecimal("125121.48")));
        assertThat(payload.currency(), is("INR"));
        assertThat(payload.marketState(), is("unknown"));
        // Carried rather than filtered: the fill path refuses a stale quote,
        // but a chart would rather draw an old point than a gap.
        assertThat(payload.stale(), is(false));
    }

    // ------------------------------------------------------ the quota guard

    @Test
    @DisplayName("the poller yields when its budget is spent, so the fill path keeps its reserve")
    void pollerStopsAtTheReserveBoundary() {
        givenSymbols(List.of("MRF.NS"));
        // 1800 spent: the poller's whole share of 2000 less the 200 reserve.
        for (int i = 0; i < 1800; i++) {
            quota.spend();
        }

        assertThat(poller.pollOnce(), is(0));

        // An order a customer is waiting on outranks a price update nobody
        // asked for. Spending the last 200 here is how orders start getting
        verifyNoInteractions(quotes);
        verify(template, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("with budget left, the poll goes ahead")
    void pollerRunsWhileTheBudgetHolds() {
        givenSymbols(List.of("MRF.NS"));
        when(quotes.quotes(anyList())).thenAnswer(inv -> quotesFor(inv.getArgument(0)));
        for (int i = 0; i < 1000; i++) {
            quota.spend();
        }

        assertThat(poller.pollOnce(), is(1));
    }

    // ---------------------------------------------------------- resilience

    @Test
    @DisplayName("a throwing poll does not escape: a scheduled method that throws may never run again")
    void aFailedCycleDoesNotKillTheSchedule() {
        givenSymbols(List.of("MRF.NS"));
        when(quotes.quotes(anyList())).thenThrow(new RuntimeException("upstream exploded"));

        // A poller that quietly stopped inside a running container is harder
        // to notice than one whose container exited: health stays green and
        assertThat(poller.pollOnce(), is(0));
    }

    @Test
    @DisplayName("a database failure reading the symbol set is survived too")
    void aDatabaseFailureIsSurvived() {
        when(mapper.findSymbolsWorthPolling()).thenThrow(new RuntimeException("pool exhausted"));

        assertThat(poller.pollOnce(), is(0));
    }

    @Test
    @DisplayName("nothing held and nothing working means no request and no message")
    void anEmptySymbolSetCostsNothing() {
        givenSymbols(List.of());

        assertThat(poller.pollOnce(), is(0));
        verifyNoInteractions(quotes);
    }

    @Test
    @DisplayName("a symbol the API could not price is skipped, and the others still publish")
    void oneMissingSymbolDoesNotCostTheOthers() {
        givenSymbols(List.of("MRF.NS", "APEX", "ITC.NS"));
        // APEX is one of the fictional Sprint 3 tickers: not in the Fauxnance
        // registry, so the batch comes back without it.
        when(quotes.quotes(anyList())).thenAnswer(inv -> {
            List<String> asked = inv.getArgument(0);
            return quotesFor(asked.stream().filter(s -> !s.equals("APEX")).toList());
        });

        assertThat(poller.pollOnce(), is(2));
    }

    // ----------------------------------------------------------- fixtures

    @SuppressWarnings("unchecked")
    private EventEnvelope<QuotePayload> captured() {
        ArgumentCaptor<EventEnvelope<QuotePayload>> sent =
                ArgumentCaptor.forClass(EventEnvelope.class);
        verify(template).send(anyString(), anyString(), sent.capture());
        return sent.getValue();
    }

    private void givenSymbols(List<String> symbols) {
        when(mapper.findSymbolsWorthPolling()).thenReturn(symbols);
    }

    private static List<String> symbols(int count) {
        return IntStream.range(0, count).mapToObj(i -> "SYM" + i + ".NS").toList();
    }

    private static Map<String, Quote> quotesFor(List<String> symbols) {
        Map<String, Quote> found = new LinkedHashMap<>();
        symbols.forEach(symbol -> found.put(symbol, quote(symbol)));
        return found;
    }

    private static Quote quote(String symbol) {
        return new Quote(symbol, new BigDecimal("125102.70693446"),
                new BigDecimal("125083.94"), new BigDecimal("125121.48"),
                new BigDecimal("3"), "INR", new BigDecimal("92.70"),
                new BigDecimal("0.074"), new BigDecimal("125010"),
                OBSERVED, "unknown", false, "synthetic");
    }
}
