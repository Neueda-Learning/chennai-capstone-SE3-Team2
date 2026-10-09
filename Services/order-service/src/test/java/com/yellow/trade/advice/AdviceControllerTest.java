package com.yellow.trade.advice;

import com.yellow.enums.Reason;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.controllers.GlobalExceptionHandler;
import com.yellow.trade.marketdata.PricingUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdviceController.class)
@Import(GlobalExceptionHandler.class)
class AdviceControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private AdviceService advice;

    @Test
    @DisplayName("a signal in the contract's shape, the disclaimer with it")
    void signal() throws Exception {
        when(advice.signal("ITC.NS")).thenReturn(new Signal("ITC.NS", Direction.BUY, 81, Methodology.NAME,
                "The 20-day average is 4.9% above the 50-day and RSI is 60, so the trend is up.",
                new SignalFigures(new BigDecimal("127.8953"), new BigDecimal("121.9153"), new BigDecimal("60.41"),
                        new BigDecimal("266.70"), Instant.parse("2026-10-07T03:59:58Z"), 81),
                Instant.parse("2026-10-07T04:00:00Z"), AdviceService.DISCLAIMER));

        mvc.perform(get("/api/v1/advice/ITC.NS"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"symbol":"ITC.NS","direction":"BUY","strength":81,
                         "methodology":"20/50-day moving average crossover, confirmed by RSI(14)",
                         "reason":"The 20-day average is 4.9% above the 50-day and RSI is 60, so the trend is up.",
                         "figures":{"sma20":127.8953,"sma50":121.9153,"rsi14":60.41,"lastPrice":266.70,
                                    "priceAsOf":"2026-10-07T03:59:58Z","days":81},
                         "computedAt":"2026-10-07T04:00:00Z",
                         "disclaimer":"Information, not advice. Computed from delayed educational data; past prices do not predict future ones."}""",
                        true));
    }

    @Test
    @DisplayName("a fund is 422 VAL-422, saying why")
    void fund() throws Exception {
        when(advice.signal("122639")).thenThrow(new AdviceExceptions.FundSignalException());

        mvc.perform(get("/api/v1/advice/122639"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("{\"errorCode\":\"VAL-422\",\"message\":\"A fund has no daily candles to read a signal from\"}", true));
    }

    @Test
    @DisplayName("a symbol nobody lists is 404 INS-404; no candles to be had, 503 MKT-503")
    void notFoundAndUnavailable() throws Exception {
        when(advice.signal("NOPE.NS")).thenThrow(new InstrumentNotFoundException("NOPE.NS", Reason.UNKNOWN));
        when(advice.signal("ITC.NS")).thenThrow(new PricingUnavailableException("no chart"));

        mvc.perform(get("/api/v1/advice/NOPE.NS")).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("INS-404"));
        mvc.perform(get("/api/v1/advice/ITC.NS")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("MKT-503"));
    }
}
