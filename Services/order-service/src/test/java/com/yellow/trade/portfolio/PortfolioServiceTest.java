package com.yellow.trade.portfolio;

import com.yellow.exceptions.InvalidOrderException;
import com.yellow.trade.dto.BalanceResponse;
import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.mappers.PositionRow;
import com.yellow.trade.marketdata.PriceQuote;
import com.yellow.trade.marketdata.PriceService;
import com.yellow.trade.marketdata.PricingUnavailableException;
import com.yellow.trade.security.AccountAccess;
import com.yellow.trade.security.AccountNotReachableException;
import com.yellow.trade.services.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T05:00:00Z");

    @Mock private AccountAccess access;
    @Mock private PositionMapper positions;
    @Mock private PriceService prices;
    @Mock private AccountService accounts;
    @Mock private RealisedMapper realised;

    private PortfolioService service() {
        return new PortfolioService(access, positions, prices, accounts, realised, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PositionRow held(String symbol, String quantity, String averagePrice) {
        PositionRow row = new PositionRow();
        row.setClientId(3L);
        row.setSymbol(symbol);
        row.setQuantity(new BigDecimal(quantity));
        row.setAveragePrice(new BigDecimal(averagePrice));
        return row;
    }

    private static PriceQuote priced(String symbol, String price) {
        return new PriceQuote(symbol, new BigDecimal(price), null, null, null, null, null, "INR", NOW, false);
    }

    @Test
    @DisplayName("the summary: cash from the account, holdings priced in one call, realised as booked, total = cash + market value")
    void summary() {
        when(positions.findByAccountId(3L)).thenReturn(List.of(held("ITC.NS", "200", "228.40"), held("SBIN.NS", "10", "800")));
        when(prices.latest(List.of("ITC.NS", "SBIN.NS")))
                .thenReturn(Map.of("ITC.NS", priced("ITC.NS", "232.71"), "SBIN.NS", priced("SBIN.NS", "780")));
        when(accounts.getBalance(3L)).thenReturn(new BalanceResponse(3L, new BigDecimal("24500.75"), "INR", NOW));
        when(realised.total(eq(3L), any(), any())).thenReturn(new BigDecimal("1145.5"));

        PortfolioSummary summary = service().summary(3L);

        assertThat(summary.baseCurrency(), is("INR"));
        assertThat(summary.cashBalance(), comparesEqualTo(new BigDecimal("24500.75")));
        assertThat(summary.marketValue(), comparesEqualTo(new BigDecimal("54342.00")));
        assertThat(summary.costBasis(), comparesEqualTo(new BigDecimal("53680.00")));
        assertThat(summary.unrealisedPnl(), comparesEqualTo(new BigDecimal("662.00")));
        assertThat(summary.realisedPnl(), comparesEqualTo(new BigDecimal("1145.50")));
        assertThat(summary.totalValue(), comparesEqualTo(new BigDecimal("78842.75")));
        assertThat(summary.positionCount(), is(2));
        assertThat(summary.partial(), is(false));
        assertThat(summary.asOf(), is(NOW));
    }

    @Test
    @DisplayName("held, and not one priced: 503 MKT-503")
    void nothingPriced() {
        when(positions.findByAccountId(3L)).thenReturn(List.of(held("ITC.NS", "200", "228.40")));
        when(prices.latest(any())).thenReturn(Map.of());

        assertThrows(PricingUnavailableException.class, () -> service().summary(3L));
        assertThrows(PricingUnavailableException.class, () -> service().positions(3L, null));
    }

    @Test
    @DisplayName("some priced: answered, partial, the others unpriced and stale")
    void somePriced() {
        when(positions.findByAccountId(3L)).thenReturn(List.of(held("ITC.NS", "200", "228.40"), held("INFY.NS", "40", "1580.25")));
        when(prices.latest(any())).thenReturn(Map.of("ITC.NS", priced("ITC.NS", "232.71")));
        when(accounts.getBalance(3L)).thenReturn(new BalanceResponse(3L, BigDecimal.TEN, "INR", NOW));
        when(realised.total(eq(3L), any(), any())).thenReturn(BigDecimal.ZERO);

        assertThat(service().summary(3L).partial(), is(true));
        assertThat(service().positions(3L, null).get(1).lastPrice(), is(nullValue()));
    }

    @Test
    @DisplayName("nothing held: answered, all zero, not partial; nothing asked of the price layer")
    void nothingHeld() {
        when(positions.findByAccountId(3L)).thenReturn(List.of());
        when(accounts.getBalance(3L)).thenReturn(new BalanceResponse(3L, BigDecimal.TEN, "INR", NOW));
        when(realised.total(eq(3L), any(), any())).thenReturn(BigDecimal.ZERO);

        PortfolioSummary summary = service().summary(3L);

        assertThat(summary.totalValue(), comparesEqualTo(BigDecimal.TEN));
        assertThat(summary.partial(), is(false));
        verifyNoInteractions(prices);
    }

    @Test
    @DisplayName("another account is ACC-403 before anything is read")
    void anotherAccount() {
        doThrow(new AccountNotReachableException()).when(access).requireOwn(4L);

        assertThrows(AccountNotReachableException.class, () -> service().summary(4L));
        assertThrows(AccountNotReachableException.class, () -> service().pnl(4L, null, null, false));
        verifyNoInteractions(positions, prices, accounts, realised);
    }

    @Test
    @DisplayName("profit and loss: realised as booked in the range, unrealised as at now, broken down when asked")
    void pnl() {
        when(positions.findByAccountId(3L)).thenReturn(List.of(held("ITC.NS", "200", "228.40")));
        when(prices.latest(any())).thenReturn(Map.of("ITC.NS", priced("ITC.NS", "232.71")));
        Instant from = Instant.parse("2026-08-31T18:30:00Z");
        Instant until = Instant.parse("2026-10-19T18:30:00Z");
        when(realised.total(3L, from, until)).thenReturn(new BigDecimal("1145.50"));
        SymbolRealised sold = new SymbolRealised();
        sold.setSymbol("SBIN.NS");
        sold.setRealised(new BigDecimal("1145.50"));
        when(realised.bySymbol(3L, from, until)).thenReturn(List.of(sold));

        PnlResponse pnl = service().pnl(3L, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-19"), true);

        assertThat(pnl.realisedPnl(), comparesEqualTo(new BigDecimal("1145.50")));
        assertThat(pnl.unrealisedPnl(), comparesEqualTo(new BigDecimal("862.00")));
        assertThat(pnl.totalPnl(), comparesEqualTo(new BigDecimal("2007.50")));
        assertThat(pnl.bySymbol().size(), is(2));
        assertThat(pnl.bySymbol().get(0).symbol(), is("ITC.NS"));
        assertThat(pnl.bySymbol().get(0).realisedPnl(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(pnl.bySymbol().get(1).symbol(), is("SBIN.NS"));
        assertThat(pnl.bySymbol().get(1).unrealisedPnl(), comparesEqualTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("from after to is VAL-422, and nothing is read")
    void fromAfterTo() {
        assertThrows(InvalidOrderException.class,
                () -> service().pnl(3L, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-09-01"), false));
        verifyNoInteractions(positions, realised);
    }

    @Test
    @DisplayName("without bySymbol there is no breakdown")
    void noBreakdown() {
        when(positions.findByAccountId(3L)).thenReturn(List.of());
        when(realised.total(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);

        assertThat(service().pnl(3L, null, null, false).bySymbol(), is(nullValue()));
        verify(realised, org.mockito.Mockito.never()).bySymbol(anyLong(), any(), any());
    }
}
