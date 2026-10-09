package com.yellow.trade.portfolio;

import com.yellow.exceptions.InvalidOrderException;
import com.yellow.trade.controllers.GlobalExceptionHandler;
import com.yellow.trade.marketdata.FauxnanceBudget;
import com.yellow.trade.marketdata.MarketDataProperties;
import com.yellow.trade.marketdata.PricingUnavailableException;
import com.yellow.trade.security.AccountNotReachableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PortfolioController.class, PortfolioHealthController.class})
@Import(GlobalExceptionHandler.class)
class PortfolioControllerTest {

    private static final Instant AT = Instant.parse("2026-10-07T05:00:00Z");

    @Autowired private MockMvc mvc;
    @MockitoBean private PortfolioService portfolio;
    @MockitoBean private JdbcTemplate jdbc;
    @MockitoBean private FauxnanceBudget budget;
    @MockitoBean private MarketDataProperties marketData;
    @MockitoBean private java.time.Clock clock;

    @Test
    @DisplayName("the summary, in the contract's shape")
    void summary() throws Exception {
        when(portfolio.summary(3L)).thenReturn(new PortfolioSummary(3L, "INR", new BigDecimal("24500.75"),
                new BigDecimal("65120.00"), new BigDecimal("61300.00"), new BigDecimal("3820.00"), new BigDecimal("6.23"),
                new BigDecimal("1145.50"), new BigDecimal("89620.75"), 3, false, AT));

        mvc.perform(get("/api/v1/portfolio/3"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"accountId":3,"baseCurrency":"INR","cashBalance":24500.75,"marketValue":65120.00,
                         "costBasis":61300.00,"unrealisedPnl":3820.00,"unrealisedPnlPercent":6.23,"realisedPnl":1145.50,
                         "totalValue":89620.75,"positionCount":3,"partial":false,"asOf":"2026-10-07T05:00:00Z"}""", true));
    }

    @Test
    @DisplayName("an unpriced position: price, value and unrealised null, stale true")
    void unpricedPosition() throws Exception {
        when(portfolio.positions(3L, "INFY.NS")).thenReturn(List.of(new PricedPosition(3L, "INFY.NS", new BigDecimal("40"),
                new BigDecimal("1580.25"), new BigDecimal("63210.00"), null, null, null, null, "INR", null, true)));

        mvc.perform(get("/api/v1/portfolio/3/positions").param("symbol", "INFY.NS"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"accountId":3,"symbol":"INFY.NS","quantity":40,"averageCost":1580.25,"costBasis":63210.00,
                          "lastPrice":null,"marketValue":null,"unrealisedPnl":null,"unrealisedPnlPercent":null,
                          "currency":"INR","priceAsOf":null,"stale":true}]""", true));
    }

    @Test
    @DisplayName("nothing priced is 503 MKT-503, Pricing unavailable")
    void pricingUnavailable() throws Exception {
        when(portfolio.summary(3L)).thenThrow(new PricingUnavailableException("no price"));

        mvc.perform(get("/api/v1/portfolio/3"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"errorCode\":\"MKT-503\",\"message\":\"Pricing unavailable\"}", true));
    }

    @Test
    @DisplayName("another account is 403 ACC-403, Account not accessible, on every route")
    void anotherAccount() throws Exception {
        when(portfolio.summary(anyLong())).thenThrow(new AccountNotReachableException());
        when(portfolio.positions(anyLong(), any())).thenThrow(new AccountNotReachableException());
        when(portfolio.pnl(anyLong(), any(), any(), anyBoolean())).thenThrow(new AccountNotReachableException());

        for (String path : new String[] {"/api/v1/portfolio/4", "/api/v1/portfolio/4/positions", "/api/v1/portfolio/4/pnl"}) {
            mvc.perform(get(path))
                    .andExpect(status().isForbidden())
                    .andExpect(content().json("{\"errorCode\":\"ACC-403\",\"message\":\"Account not accessible\"}", true));
        }
    }

    @Test
    @DisplayName("profit and loss: the range and the breakdown are passed on; without bySymbol there is no breakdown field")
    void pnl() throws Exception {
        when(portfolio.pnl(3L, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-19"), false)).thenReturn(
                new PnlResponse(3L, "INR", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-19"),
                        new BigDecimal("1145.50"), new BigDecimal("3820.00"), new BigDecimal("4965.50"), null, AT));

        mvc.perform(get("/api/v1/portfolio/3/pnl").param("from", "2026-09-01").param("to", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-01"))
                .andExpect(jsonPath("$.totalPnl").value(4965.50))
                .andExpect(jsonPath("$.bySymbol").doesNotExist());
        mvc.perform(get("/api/v1/portfolio/3/pnl").param("bySymbol", "true"));
        verify(portfolio).pnl(eq(3L), eq(null), eq(null), eq(true));
    }

    @Test
    @DisplayName("a date it cannot read, or from after to, is VAL-422")
    void invalidDates() throws Exception {
        when(portfolio.pnl(3L, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-09-01"), false))
                .thenThrow(new InvalidOrderException("from", "2026-10-19"));

        mvc.perform(get("/api/v1/portfolio/3/pnl").param("from", "yesterday"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errorCode").value("VAL-422"));
        mvc.perform(get("/api/v1/portfolio/3/pnl").param("from", "2026-10-19").param("to", "2026-09-01"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    @DisplayName("health: 200 with each dependency; a spent budget degrades it rather than failing it")
    void health() throws Exception {
        when(clock.instant()).thenReturn(AT);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(marketData.fauxnanceApiKey()).thenReturn("a-key");
        when(budget.remaining()).thenReturn(1873, 0);

        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"status":"ok","dependencies":[{"name":"postgres","status":"ok","quotaRemaining":null},
                         {"name":"fauxnance","status":"ok","quotaRemaining":1873}],"asOf":"2026-10-07T05:00:00Z"}""", true));
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.dependencies[1].status").value("degraded"));
    }
}
