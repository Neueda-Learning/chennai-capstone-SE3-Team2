package com.yellow.trade.strategy;

import com.yellow.enums.OrderSide;
import com.yellow.enums.Reason;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.controllers.GlobalExceptionHandler;
import com.yellow.trade.security.AccountNotReachableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StrategyController.class)
@Import(GlobalExceptionHandler.class)
class StrategyControllerTest {

    private static final String BUY_THE_DIP = """
            {"symbol":"ITC.NS","side":"BUY","quantity":2,"trigger":"FALLS_THROUGH",
             "triggerPrice":250.00,"maxSpend":600,"maxPosition":20}""";

    @Autowired private MockMvc mvc;
    @MockitoBean private StrategyService strategies;

    private static Strategy strategy(boolean enabled) {
        return new Strategy(7, "ITC.NS", OrderSide.BUY, 2, Trigger.FALLS_THROUGH, new BigDecimal("250.00"),
                new BigDecimal("600.0000"), 20, enabled, StrategyStatus.ARMED, 0,
                Instant.parse("2026-10-07T04:00:00Z"), null);
    }

    @Test
    @DisplayName("created disabled, 201, in the contract's shape")
    void create() throws Exception {
        when(strategies.create(eq(3L), any())).thenReturn(strategy(false));

        mvc.perform(post("/api/v1/accounts/3/strategies").contentType(MediaType.APPLICATION_JSON).content(BUY_THE_DIP))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"id":7,"symbol":"ITC.NS","side":"BUY","quantity":2,"trigger":"FALLS_THROUGH",
                         "triggerPrice":250.00,"maxSpend":600.0000,"maxPosition":20,"enabled":false,
                         "status":"ARMED","failures":0,"createdAt":"2026-10-07T04:00:00Z","lastFiredAt":null}""", true));
    }

    @Test
    @DisplayName("a request the contract refuses is 422 VAL-422, before the service sees it")
    void invalid() throws Exception {
        mvc.perform(post("/api/v1/accounts/3/strategies").contentType(MediaType.APPLICATION_JSON)
                        .content(BUY_THE_DIP.replace("\"quantity\":2", "\"quantity\":0")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        mvc.perform(post("/api/v1/accounts/3/strategies").contentType(MediaType.APPLICATION_JSON)
                        .content(BUY_THE_DIP.replace("250.00", "250.005")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/accounts/3/strategies").contentType(MediaType.APPLICATION_JSON)
                        .content(BUY_THE_DIP.replace("FALLS_THROUGH", "WHENEVER")))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(strategies);
    }

    @Test
    @DisplayName("the eleventh is 409 LIM-409; a fund 422; a symbol nobody lists 404 INS-404")
    void refusals() throws Exception {
        when(strategies.create(eq(3L), any()))
                .thenThrow(new StrategyExceptions.LimitReachedException())
                .thenThrow(new StrategyExceptions.FundStrategyException())
                .thenThrow(new InstrumentNotFoundException("NOPE.NS", Reason.UNKNOWN));

        mvc.perform(post("/api/v1/accounts/3/strategies").contentType(MediaType.APPLICATION_JSON).content(BUY_THE_DIP))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"errorCode\":\"LIM-409\",\"message\":\"At most 10 strategies an account\"}", true));
        mvc.perform(post("/api/v1/accounts/3/strategies").contentType(MediaType.APPLICATION_JSON).content(BUY_THE_DIP))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        mvc.perform(post("/api/v1/accounts/3/strategies").contentType(MediaType.APPLICATION_JSON).content(BUY_THE_DIP))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INS-404"));
    }

    @Test
    @DisplayName("switched on and off with PUT; a strategy not on this account is 404 STR-404")
    void enabled() throws Exception {
        when(strategies.setEnabled(3L, 7L, true)).thenReturn(strategy(true));
        when(strategies.setEnabled(3L, 8L, false)).thenThrow(new StrategyExceptions.NotFoundException());

        mvc.perform(put("/api/v1/accounts/3/strategies/7/enabled").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(put("/api/v1/accounts/3/strategies/8/enabled").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"errorCode\":\"STR-404\",\"message\":\"Strategy not found\"}", true));
    }

    @Test
    @DisplayName("deleted with 204; its runs read newest first")
    void deleteAndRuns() throws Exception {
        when(strategies.runs(3L, 7L)).thenReturn(List.of(
                new StrategyRun(2, Instant.parse("2026-10-07T04:01:00Z"), new BigDecimal("249.10"), RunOutcome.FILLED, null),
                new StrategyRun(1, Instant.parse("2026-10-07T04:00:30Z"), new BigDecimal("249.80"), RunOutcome.PLACED, null)));

        mvc.perform(get("/api/v1/accounts/3/strategies/7/runs"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":2,"at":"2026-10-07T04:01:00Z","quotePrice":249.10,"outcome":"FILLED","reason":null},
                         {"id":1,"at":"2026-10-07T04:00:30Z","quotePrice":249.80,"outcome":"PLACED","reason":null}]""", true));
        mvc.perform(delete("/api/v1/accounts/3/strategies/7")).andExpect(status().isNoContent());
        verify(strategies).delete(3L, 7L);
    }

    @Test
    @DisplayName("another customer's account is 403 ACC-403")
    void otherAccount() throws Exception {
        doThrow(new AccountNotReachableException()).when(strategies).list(4L);

        mvc.perform(get("/api/v1/accounts/4/strategies"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"));
    }
}
