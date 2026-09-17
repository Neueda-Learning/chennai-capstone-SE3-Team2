package com.yellow.executor.consume;

import com.yellow.executor.fill.FillDecision;
import com.yellow.executor.fill.OrderSnapshot;
import com.yellow.executor.fill.RejectReason;
import com.yellow.executor.persistence.AccountRow;
import com.yellow.executor.persistence.ExecutableOrderRow;
import com.yellow.executor.persistence.ExecutionMapper;
import com.yellow.executor.persistence.PositionRow;
import com.yellow.executor.quotes.Quote;
import com.yellow.executor.quotes.QuoteSource;
import com.yellow.executor.quotes.QuoteUnavailableException;
import com.yellow.executor.settle.SettlementPort;
import com.yellow.executor.settle.SettlementResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The order of operations, and what each step is allowed to cost.
 *
 * <p>Several of these assert that something did NOT happen -- no quote was
 * fetched, nothing was settled. Those are the interesting ones: the cost of
 * getting the sequence wrong is a wasted request out of 2000, or an order
 * settled twice, and neither shows up in a test that only checks the happy
 * path's return value.
 */
class OrderExecutionServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");

    private ExecutionMapper mapper;
    private QuoteSource quotes;
    private SettlementPort settlement;
    private OrderExecutionService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void wire() {
        mapper = mock(ExecutionMapper.class);
        quotes = mock(QuoteSource.class);
        settlement = mock(SettlementPort.class);
        service = new OrderExecutionService(mapper, quotes, settlement,
                mock(KafkaTemplate.class),
                Clock.fixed(Instant.parse("2026-09-17T09:14:24Z"), ZoneOffset.UTC));

        when(settlement.settle(any(), any())).thenReturn(SettlementResult.SETTLED);
    }

    // ------------------------------------------------------------- the fill

    @Test
    @DisplayName("a marketable BUY settles at the ask")
    void marketableBuyFillsAtTheAsk() {
        givenOrder(order("BUY", "4", "126000.00", "NEW", "STOCK", true));
        givenAccount("750000", "0", "ACTIVE");
        when(quotes.quote("MRF.NS")).thenReturn(quote());

        service.execute(ORDER_ID);

        FillDecision decision = settledDecision();
        assertThat(decision.isFill(), is(true));
        assertThat(((FillDecision.Fill) decision).executedPrice(),
                comparesEqualTo(new BigDecimal("125121.4800")));
    }

    @Test
    @DisplayName("an unmarketable BUY is rejected as PRICE_NOT_MET")
    void unmarketableBuyIsRejected() {
        givenOrder(order("BUY", "4", "125000.00", "NEW", "STOCK", true));
        givenAccount("750000", "0", "ACTIVE");
        when(quotes.quote("MRF.NS")).thenReturn(quote());

        service.execute(ORDER_ID);

        assertThat(settledDecision(), is(new FillDecision.Reject(RejectReason.PRICE_NOT_MET)));
    }

    // -------------------------------------------------- the duplicate guard

    @Test
    @DisplayName("an order already settled is left alone, and costs no quota")
    void alreadySettledOrderIsSkipped() {
        givenOrder(order("BUY", "4", "126000.00", "FILLED", "STOCK", true));

        Optional<SettlementResult> result = service.execute(ORDER_ID);

        assertThat(result.isEmpty(), is(true));
        // Neither a request nor a write. A duplicate arriving well after the
        // first delivery is ordinary, and it should be cheap.
        verifyNoInteractions(quotes);
        verifyNoInteractions(settlement);
    }

    // --------------------------------------------- checks before the quote

    @Test
    @DisplayName("a delisted instrument is rejected without spending a request")
    void delistedInstrumentCostsNoQuota() {
        givenOrder(order("BUY", "4", "126000.00", "NEW", "STOCK", false));
        givenAccount("750000", "0", "ACTIVE");

        service.execute(ORDER_ID);

        assertThat(settledDecision(),
                is(new FillDecision.Reject(RejectReason.INSTRUMENT_NOT_TRADABLE)));
        verifyNoInteractions(quotes);
    }

    @Test
    @DisplayName("an account suspended after acceptance does not trade, and costs no request")
    void suspendedAccountDoesNotTrade() {
        givenOrder(order("BUY", "4", "126000.00", "NEW", "STOCK", true));
        givenAccount("750000", "0", "SUSPENDED");

        service.execute(ORDER_ID);

        assertThat(settledDecision(),
                is(new FillDecision.Reject(RejectReason.ACCOUNT_NOT_ACTIVE)));
        verifyNoInteractions(quotes);
    }

    @Test
    @DisplayName("a mutual fund is rejected as not priceable, and no quote is asked for")
    void mutualFundIsNotPriceable() {
        givenOrder(order("BUY", "10", "45.00", "NEW", "MF", true));
        givenAccount("750000", "0", "ACTIVE");

        service.execute(ORDER_ID);

        assertThat(settledDecision(),
                is(new FillDecision.Reject(RejectReason.INSTRUMENT_NOT_PRICEABLE)));
        // Fauxnance has no funds in its registry, so this would be a 404 that
        // still cost one of the day's requests.
        verifyNoInteractions(quotes);
    }

    // --------------------------------------------------------- no price

    @Test
    @DisplayName("an order with no available price is RESOLVED, not left at NEW and not dead-lettered")
    void unpriceableOrderIsResolved() {
        givenOrder(order("BUY", "4", "126000.00", "NEW", "STOCK", true));
        givenAccount("750000", "0", "ACTIVE");
        when(quotes.quote("MRF.NS"))
                .thenThrow(new QuoteUnavailableException("upstream down", "MRF.NS", 3));

        Optional<SettlementResult> result = service.execute(ORDER_ID);

        // The customer is told. A price feed being down is a business outcome,
        // and an order that never resolves serves them worse than a rejection.
        assertThat(settledDecision(), is(new FillDecision.Reject(RejectReason.NO_PRICE)));
        assertThat(result, is(Optional.of(SettlementResult.SETTLED)));
    }

    @Test
    @DisplayName("an unknown symbol resolves the same way, which is how APEX demonstrates the path")
    void unknownSymbolResolvesAsNoPrice() {
        givenOrder(order("BUY", "10", "1450.00", "NEW", "STOCK", true));
        givenAccount("750000", "0", "ACTIVE");
        when(quotes.quote(anyString()))
                .thenThrow(new QuoteUnavailableException("unknown symbol", "APEX", 1));

        service.execute(ORDER_ID);

        assertThat(settledDecision(), is(new FillDecision.Reject(RejectReason.NO_PRICE)));
    }

    // ------------------------------------------------ re-checks at execution

    @Test
    @DisplayName("a BUY the account can no longer afford is rejected after pricing")
    void unaffordableAtExecutionIsRejected() {
        // Marketable at the ask, but the balance moved while the order waited.
        givenOrder(order("BUY", "5", "126000.00", "NEW", "STOCK", true));
        givenAccount("100000", "0", "ACTIVE");
        when(quotes.quote("MRF.NS")).thenReturn(quote());

        service.execute(ORDER_ID);

        assertThat(settledDecision(),
                is(new FillDecision.Reject(RejectReason.INSUFFICIENT_FUNDS)));
    }

    @Test
    @DisplayName("a SELL whose holding disappeared is rejected after pricing")
    void missingHoldingIsRejected() {
        givenOrder(order("SELL", "10", "100000.00", "NEW", "STOCK", true));
        givenAccount("750000", "0", "ACTIVE");
        when(quotes.quote("MRF.NS")).thenReturn(quote());
        when(mapper.findPosition(any(), any(), anyString())).thenReturn(null);

        service.execute(ORDER_ID);

        assertThat(settledDecision(),
                is(new FillDecision.Reject(RejectReason.INSUFFICIENT_HOLDINGS)));
    }

    @Test
    @DisplayName("a SELL with the holding intact fills at the bid")
    void sellWithHoldingFillsAtBid() {
        givenOrder(order("SELL", "10", "100000.00", "NEW", "STOCK", true));
        givenAccount("750000", "0", "ACTIVE");
        when(quotes.quote("MRF.NS")).thenReturn(quote());
        when(mapper.findPosition(any(), any(), anyString())).thenReturn(position("10"));

        service.execute(ORDER_ID);

        assertThat(((FillDecision.Fill) settledDecision()).executedPrice(),
                comparesEqualTo(new BigDecimal("125083.9400")));
    }

    // ----------------------------------------------------- poison messages

    @Test
    @DisplayName("an order id that is not in Postgres throws rather than resolving anything")
    void unknownOrderThrows() {
        when(mapper.findOrder(ORDER_ID)).thenReturn(null);

        // Dead-lettered by story 613 on the first attempt: no number of retries
        // puts a row in a table, and retrying blocks the partition.
        assertThrows(UnknownOrderException.class, () -> service.execute(ORDER_ID));
        verify(settlement, never()).settle(any(), any());
    }

    // ----------------------------------------------------------- fixtures

    private FillDecision settledDecision() {
        ArgumentCaptor<FillDecision> captor = ArgumentCaptor.forClass(FillDecision.class);
        verify(settlement).settle(captor.capture(), any(OrderSnapshot.class));
        return captor.getValue();
    }

    private void givenOrder(ExecutableOrderRow row) {
        when(mapper.findOrder(ORDER_ID)).thenReturn(row);
    }

    private void givenAccount(String balance, String blocked, String status) {
        AccountRow row = new AccountRow();
        row.setClientId(3L);
        row.setAccountRef("ACC-000003");
        row.setBalance(new BigDecimal(balance));
        row.setBlockedFunds(new BigDecimal(blocked));
        row.setStatus(status);
        row.setVersion(7);
        when(mapper.findAccount(3L)).thenReturn(row);
    }

    private static ExecutableOrderRow order(String side, String quantity, String limit,
                                            String status, String instrumentType, boolean tradable) {
        ExecutableOrderRow row = new ExecutableOrderRow();
        row.setOrderId(ORDER_ID);
        row.setClientId(3L);
        row.setInstrumentId(1L);
        row.setSymbol("MRF.NS");
        row.setSide(side);
        row.setQuantity(new BigDecimal(quantity));
        row.setPrice(new BigDecimal(limit));
        row.setStatus(status);
        row.setInstrumentType(instrumentType);
        row.setInstrumentName("MRF Limited");
        row.setTradable(tradable);
        return row;
    }

    private static PositionRow position(String quantity) {
        PositionRow row = new PositionRow();
        row.setPositionId(1L);
        row.setClientId(3L);
        row.setInstrumentId(1L);
        row.setQuantity(new BigDecimal(quantity));
        row.setAveragePrice(new BigDecimal("120000.0000"));
        return row;
    }

    private static Quote quote() {
        return new Quote("MRF.NS", new BigDecimal("125102.70693446"),
                new BigDecimal("125083.94"), new BigDecimal("125121.48"),
                new BigDecimal("3"), "INR", new BigDecimal("92.70"),
                new BigDecimal("0.074"), new BigDecimal("125010"),
                Instant.parse("2026-09-17T05:05:00Z"), "unknown", false, "synthetic");
    }
}
