package com.yellow.trade.marketdata;

import com.yellow.enums.Reason;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.controllers.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketDataController.class)
@Import(GlobalExceptionHandler.class)
class MarketDataControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private PriceService prices;
    @MockitoBean private CandleService candles;

    @Test
    @DisplayName("quotes each listed symbol in the order asked; one that cannot be priced has no price and is stale")
    void quotes() throws Exception {
        when(prices.quotes(List.of("MRF.NS", "122639"))).thenReturn(List.of(
                new PriceQuote("MRF.NS", new BigDecimal("123525.00"), new BigDecimal("-1755.00"),
                        new BigDecimal("-1.40"), new BigDecimal("125280.00"), new BigDecimal("123500.00"),
                        new BigDecimal("123550.00"), "INR", Instant.parse("2026-10-06T04:19:31Z"), false),
                PriceQuote.unpriced("122639")));

        mvc.perform(get("/api/v1/quotes").param("symbols", "MRF.NS,122639"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"symbol":"MRF.NS","price":123525.00,"change":-1755.00,"changePercent":-1.40,
                          "previousClose":125280.00,"bid":123500.00,"ask":123550.00,"currency":"INR",
                          "asOf":"2026-10-06T04:19:31Z","stale":false},
                         {"symbol":"122639","price":null,"change":null,"changePercent":null,
                          "previousClose":null,"bid":null,"ask":null,"currency":"INR","asOf":null,
                          "stale":true}]""", true));
    }

    @Test
    @DisplayName("refuses no symbols, or more than 50, with VAL-422 and asks nobody")
    void symbolBounds() throws Exception {
        mvc.perform(get("/api/v1/quotes"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        mvc.perform(get("/api/v1/quotes").param("symbols", String.join(",", Collections.nCopies(51, "ITC.NS"))))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(prices);
    }

    @Test
    @DisplayName("a chart is the symbol, its range and daily candles, oldest first")
    void chart() throws Exception {
        when(candles.candles("MRF.NS", ChartRange.SIX_MONTHS)).thenReturn(List.of(
                new Candle(LocalDate.parse("2026-10-05"), new BigDecimal("125000"), new BigDecimal("126100.5"),
                        new BigDecimal("124000"), new BigDecimal("125280"), 8541L)));

        mvc.perform(get("/api/v1/instruments/MRF.NS/candles").param("range", "6M"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"symbol":"MRF.NS","range":"6M","candles":[
                          {"date":"2026-10-05","open":125000,"high":126100.5,"low":124000,"close":125280,
                           "volume":8541}]}""", true));
    }

    @Test
    @DisplayName("the range defaults to six months, and anything but 1M, 3M, 6M, 1Y or 5Y is VAL-422")
    void ranges() throws Exception {
        when(candles.candles("MRF.NS", ChartRange.SIX_MONTHS)).thenReturn(List.of());

        mvc.perform(get("/api/v1/instruments/MRF.NS/candles"))
                .andExpect(jsonPath("$.range").value("6M"));
        mvc.perform(get("/api/v1/instruments/MRF.NS/candles").param("range", "2W"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    @DisplayName("an unknown symbol is INS-404; a fund, which has no chart yet, is VAL-422")
    void refusals() throws Exception {
        when(candles.candles("NOPE.NS", ChartRange.ONE_YEAR))
                .thenThrow(new InstrumentNotFoundException("NOPE.NS", Reason.UNKNOWN));
        when(candles.candles("122639", ChartRange.ONE_YEAR)).thenThrow(new ChartUnavailableException("122639"));

        mvc.perform(get("/api/v1/instruments/NOPE.NS/candles").param("range", "1Y"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INS-404"));
        mvc.perform(get("/api/v1/instruments/122639/candles").param("range", "1Y"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    @DisplayName("no price source answering is 503 MKT-503, the code the portfolio contract uses")
    void pricingUnavailable() throws Exception {
        when(candles.candles("MRF.NS", ChartRange.ONE_MONTH)).thenThrow(new PricingUnavailableException("down"));

        mvc.perform(get("/api/v1/instruments/MRF.NS/candles").param("range", "1M"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"errorCode\":\"MKT-503\",\"message\":\"Pricing unavailable\"}", true));
    }
}
