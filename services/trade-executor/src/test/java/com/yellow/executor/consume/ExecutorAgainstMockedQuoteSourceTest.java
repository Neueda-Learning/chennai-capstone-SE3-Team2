package com.yellow.executor.consume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.yellow.executor.events.EventEnvelope;
import com.yellow.executor.events.TradeEventPayload;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import com.yellow.executor.config.FauxnanceProperties;
import com.yellow.executor.persistence.AccountRow;
import com.yellow.executor.persistence.ExecutableOrderRow;
import com.yellow.executor.persistence.ExecutionMapper;
import com.yellow.executor.persistence.PositionRow;
import com.yellow.executor.quotes.FauxnanceQuoteClient;
import com.yellow.executor.quotes.QuotaCounter;
import com.yellow.executor.settle.GuardedSettlement;
import com.yellow.executor.settle.SettlementResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * The executor end to end against a mocked quote source, covering a fill, a
 * reject and a pricing-unavailable response -- the integration test story 610
 * asks for.
 */
class ExecutorAgainstMockedQuoteSourceTest {

    private static final UUID ORDER_ID = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
    private static final Instant NOW = Instant.parse("2026-09-17T09:14:24Z");

    private WireMockServer fauxnance;
    private InMemoryExecutionMapper mapper;
    private OrderExecutionService service;

    @BeforeEach
    void wire() {
        fauxnance = new WireMockServer(options().dynamicPort());
        fauxnance.start();

        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        mapper = new InMemoryExecutionMapper();

        FauxnanceProperties props = new FauxnanceProperties(
                "http://localhost:" + fauxnance.port(), "test-key",
                Duration.ofSeconds(5), 2, Duration.ofMillis(10), Duration.ofMillis(50),
                2000, 200);

        service = new OrderExecutionService(
                mapper,
                new FauxnanceQuoteClient(props, new QuotaCounter(fixed), new ObjectMapper()),
                new GuardedSettlement(mapper, fixed),
                mockTemplate(),
                fixed);
    }

    @AfterEach
    void stop() {
        fauxnance.stop();
    }

    @Test
    @DisplayName("a fill: the order settles FILLED at the ask, carrying the price and the time")
    void aFill() {
        mapper.given(order("BUY", "4", "126000.00"), account("750000", "ACTIVE"));
        fauxnance.stubFor(get(urlPathEqualTo("/quotes/MRF.NS")).willReturn(quoteResponse(false)));

        assertThat(service.execute(ORDER_ID), is(Optional.of(SettlementResult.SETTLED)));

        ExecutableOrderRow settled = mapper.findOrder(ORDER_ID);
        assertThat(settled.getStatus(), is("FILLED"));
        assertThat(mapper.lastFillPrice, comparesEqualTo(new BigDecimal("125121.4800")));
        assertThat(mapper.lastResolvedAt, is(NOW));
        // A filled order carries no reason. The database enforces this too.
        assertThat(mapper.lastReason, is(nullValue()));
    }

    @Test
    @DisplayName("a reject: an unmarketable order settles REJECTED with PRICE_NOT_MET and no price")
    void aReject() {
        mapper.given(order("BUY", "4", "125000.00"), account("750000", "ACTIVE"));
        fauxnance.stubFor(get(urlPathEqualTo("/quotes/MRF.NS")).willReturn(quoteResponse(false)));

        assertThat(service.execute(ORDER_ID), is(Optional.of(SettlementResult.SETTLED)));

        assertThat(mapper.findOrder(ORDER_ID).getStatus(), is("REJECTED"));
        assertThat(mapper.lastReason, is("PRICE_NOT_MET"));
        // A rejected order never carries a fill price.
        assertThat(mapper.lastFillPrice, is(nullValue()));
    }

    @Test
    @DisplayName("pricing unavailable: the order is RESOLVED as NO_PRICE rather than left at NEW")
    void pricingUnavailable() {
        mapper.given(order("BUY", "4", "126000.00"), account("750000", "ACTIVE"));
        // 503 on every attempt: the upstream Fauxnance depends on is down.
        fauxnance.stubFor(get(urlPathEqualTo("/quotes/MRF.NS"))
                .willReturn(aResponse().withStatus(503)));

        assertThat(service.execute(ORDER_ID), is(Optional.of(SettlementResult.SETTLED)));

        // The customer gets an answer. This is the criterion: an order that
        // cannot be priced is resolved rather than left at NEW for ever.
        assertThat(mapper.findOrder(ORDER_ID).getStatus(), is("REJECTED"));
        assertThat(mapper.lastReason, is("NO_PRICE"));
        // Retried inside the client before giving up, not on the first refusal.
        fauxnance.verify(2, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/quotes/MRF.NS")));
    }

    @Test
    @DisplayName("an unknown symbol takes the same path, which is how a fictional ticker demonstrates it")
    void unknownSymbolResolves() {
        mapper.given(order("BUY", "4", "126000.00"), account("750000", "ACTIVE"));
        fauxnance.stubFor(get(urlPathEqualTo("/quotes/MRF.NS"))
                .willReturn(aResponse().withStatus(404)));

        service.execute(ORDER_ID);

        assertThat(mapper.findOrder(ORDER_ID).getStatus(), is("REJECTED"));
        assertThat(mapper.lastReason, is("NO_PRICE"));
        // Not retried: no number of attempts invents a symbol.
        fauxnance.verify(1, com.github.tomakehurst.wiremock.client.WireMock
                .getRequestedFor(urlPathEqualTo("/quotes/MRF.NS")));
    }

    @Test
    @DisplayName("a stale quote on every attempt resolves as NO_PRICE rather than filling at an expired price")
    void permanentlyStaleResolves() {
        mapper.given(order("BUY", "4", "126000.00"), account("750000", "ACTIVE"));
        fauxnance.stubFor(get(urlPathEqualTo("/quotes/MRF.NS")).willReturn(quoteResponse(true)));

        service.execute(ORDER_ID);

        assertThat(mapper.findOrder(ORDER_ID).getStatus(), is("REJECTED"));
        assertThat(mapper.lastReason, is("NO_PRICE"));
    }

    @Test
    @DisplayName("a second delivery of the same order changes nothing and settles nothing")
    void secondDeliveryIsHarmless() {
        mapper.given(order("BUY", "4", "126000.00"), account("750000", "ACTIVE"));
        fauxnance.stubFor(get(urlPathEqualTo("/quotes/MRF.NS")).willReturn(quoteResponse(false)));

        service.execute(ORDER_ID);
        int requestsAfterFirst = fauxnance.getAllServeEvents().size();

        Optional<SettlementResult> second = service.execute(ORDER_ID);

        // Nothing to settle, and no event for story 611 to publish.
        assertThat(second.isEmpty(), is(true));
        assertThat(mapper.settleAttempts, is(1));
        // And the duplicate did not spend a request out of the daily 2000.
        assertThat(fauxnance.getAllServeEvents().size(), is(requestsAfterFirst));
    }

    @SuppressWarnings("unchecked")
    private static KafkaTemplate<String, EventEnvelope<TradeEventPayload>> mockTemplate() {
        return (KafkaTemplate<String, EventEnvelope<TradeEventPayload>>)
                (Object) Mockito.mock(KafkaTemplate.class);
    }

    // ----------------------------------------------------------- fixtures

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
            quoteResponse(boolean stale) {
        return aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "data": {"symbol": "MRF.NS", "price": 125102.70693446,
                                   "bid": 125083.94, "ask": 125121.48, "spreadBps": 3,
                                   "currency": "INR", "change": 92.70, "changePercent": 0.074,
                                   "previousClose": 125010, "asOf": "2026-09-17T05:05:00Z",
                                   "marketState": "unknown"},
                          "meta": {"asOf": "2026-09-17T05:05:00Z", "symbol": "MRF.NS",
                                   "disclaimer": "Educational data. Not for investment use.",
                                   "source": "synthetic", "stale": %s, "spreadSource": "modelled"}
                        }
                        """.formatted(stale));
    }

    private static ExecutableOrderRow order(String side, String quantity, String limit) {
        ExecutableOrderRow row = new ExecutableOrderRow();
        row.setOrderId(ORDER_ID);
        row.setClientId(3L);
        row.setInstrumentId(1L);
        row.setSymbol("MRF.NS");
        row.setSide(side);
        row.setQuantity(new BigDecimal(quantity));
        row.setPrice(new BigDecimal(limit));
        row.setStatus("NEW");
        row.setInstrumentType("STOCK");
        row.setInstrumentName("MRF Limited");
        row.setTradable(true);
        return row;
    }

    private static AccountRow account(String balance, String status) {
        AccountRow row = new AccountRow();
        row.setClientId(3L);
        row.setAccountRef("ACC-000003");
        row.setBalance(new BigDecimal(balance));
        row.setBlockedFunds(BigDecimal.ZERO);
        row.setStatus(status);
        row.setVersion(7);
        return row;
    }

    /**
     * An in-memory stand-in for the database, with the one behaviour that
     * matters reproduced faithfully: settleIfNew affects a row only while the
     * status is still NEW.
     */
    private static final class InMemoryExecutionMapper implements ExecutionMapper {

        private final Map<UUID, ExecutableOrderRow> orders = new HashMap<>();
        private final Map<Long, AccountRow> accounts = new HashMap<>();
        private final Map<String, PositionRow> positions = new HashMap<>();

        int settleAttempts;
        BigDecimal lastFillPrice;
        Instant lastResolvedAt;
        String lastReason;

        void given(ExecutableOrderRow order, AccountRow account) {
            orders.put(order.getOrderId(), order);
            accounts.put(account.getClientId(), account);
        }

        @Override
        public ExecutableOrderRow findOrder(UUID orderId) {
            return orders.get(orderId);
        }

        @Override
        public AccountRow findAccount(Long accountId) {
            return accounts.get(accountId);
        }

        @Override
        public PositionRow findPosition(Long accountId, Long instrumentId, String positionType) {
            return positions.get(accountId + ":" + instrumentId + ":" + positionType);
        }

        @Override
        public int settleIfNew(UUID orderId, String status, BigDecimal fillPrice,
                               Instant resolvedAt, String rejectionReason) {
            ExecutableOrderRow row = orders.get(orderId);
            if (row == null || !"NEW".equals(row.getStatus())) {
                return 0;
            }
            settleAttempts++;
            row.setStatus(status);
            lastFillPrice = fillPrice;
            lastResolvedAt = resolvedAt;
            lastReason = rejectionReason;
            return 1;
        }

        @Override
        public List<String> findSymbolsWorthPolling() {
            return new ArrayList<>();
        }

        @Override
        public int updateAccount(Long accountId, BigDecimal balanceDelta,
                                 BigDecimal blockedDelta, int expectedVersion) {
            AccountRow row = accounts.get(accountId);
            if (row == null || row.getVersion() != expectedVersion) return 0;
            row.setBalance(row.getBalance().add(balanceDelta));
            row.setBlockedFunds(row.getBlockedFunds().add(blockedDelta));
            row.setVersion(row.getVersion() + 1);
            return 1;
        }

        @Override
        public void insertPosition(Long accountId, Long instrumentId,
                                   String positionType, BigDecimal quantity,
                                   BigDecimal averagePrice) {
            PositionRow row = new PositionRow();
            row.setClientId(accountId);
            row.setInstrumentId(instrumentId);
            row.setQuantity(quantity);
            row.setAveragePrice(averagePrice);
            positions.put(accountId + ":" + instrumentId + ":" + positionType, row);
        }

        @Override
        public void updatePosition(Long accountId, Long instrumentId,
                                   String positionType, BigDecimal quantity,
                                   BigDecimal averagePrice) {
            PositionRow row = positions.get(accountId + ":" + instrumentId + ":" + positionType);
            if (row != null) {
                row.setQuantity(quantity);
                row.setAveragePrice(averagePrice);
            }
        }

        @Override
        public void deletePosition(Long accountId, Long instrumentId, String positionType) {
            positions.remove(accountId + ":" + instrumentId + ":" + positionType);
        }
    }
}
