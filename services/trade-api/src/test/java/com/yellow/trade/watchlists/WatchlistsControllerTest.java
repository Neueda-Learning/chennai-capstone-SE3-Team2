package com.yellow.trade.watchlists;

import com.yellow.enums.Reason;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.controllers.GlobalExceptionHandler;
import com.yellow.trade.security.AccountNotReachableException;
import com.yellow.trade.watchlists.WatchExceptions.FundAlertException;
import com.yellow.trade.watchlists.WatchExceptions.LimitReachedException;
import com.yellow.trade.watchlists.WatchExceptions.NotFoundException;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

@WebMvcTest({WatchlistsController.class, AlertsController.class})
@Import(GlobalExceptionHandler.class)
class WatchlistsControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private WatchlistService watchlists;
    @MockitoBean private AlertService alerts;

    @Test
    @DisplayName("answers the watchlists in the contract's shape, each entry with the price the stream carried")
    void lists() throws Exception {
        when(watchlists.list(3L)).thenReturn(List.of(new Watchlist(1L, "Long term", 1, List.of(
                new WatchlistItem("ITC.NS", 1, new BigDecimal("266.70"), new BigDecimal("-0.82"),
                        Instant.parse("2026-10-06T09:45:00Z"), false),
                new WatchlistItem("120716", 2, null, null, null, false)))));

        mvc.perform(get("/api/v1/accounts/3/watchlists"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":1,"name":"Long term","position":1,"items":[
                          {"symbol":"ITC.NS","position":1,"lastPrice":266.70,"changePercent":-0.82,
                           "priceAsOf":"2026-10-06T09:45:00Z","stale":false},
                          {"symbol":"120716","position":2,"lastPrice":null,"changePercent":null,"priceAsOf":null,
                           "stale":false}]}]""", true));
    }

    @Test
    @DisplayName("creates one: 201; a blank or over-long name, or a field it does not know, is VAL-422")
    void create() throws Exception {
        when(watchlists.create(3L, "Banks")).thenReturn(new Watchlist(2L, "Banks", 2, List.of()));

        mvc.perform(post("/api/v1/accounts/3/watchlists").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Banks\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
        for (String body : new String[] {"{\"name\":\"  \"}", "{\"name\":\"" + "x".repeat(41) + "\"}", "{\"name\":\"a\",\"owner\":4}"}) {
            mvc.perform(post("/api/v1/accounts/3/watchlists").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        }
    }

    @Test
    @DisplayName("a sixth watchlist is 409 LIM-409")
    void cap() throws Exception {
        when(watchlists.create(eq(3L), anyString())).thenThrow(new LimitReachedException("At most 5 watchlists an account"));

        mvc.perform(post("/api/v1/accounts/3/watchlists").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sixth\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"errorCode\":\"LIM-409\",\"message\":\"At most 5 watchlists an account\"}", true));
    }

    @Test
    @DisplayName("adds an instrument by its symbol, dots and ampersands included: 204")
    void addsItem() throws Exception {
        mvc.perform(put("/api/v1/accounts/3/watchlists/1/items/{symbol}", "M&M.NS")).andExpect(status().isNoContent());

        verify(watchlists).addItem(3L, 1L, "M&M.NS");
    }

    @Test
    @DisplayName("another account's watchlist is 404 WCH-404; an instrument nobody lists, 404 INS-404")
    void notFound() throws Exception {
        doThrow(new NotFoundException("Watchlist")).when(watchlists).addItem(3L, 77L, "ITC.NS");
        doThrow(new InstrumentNotFoundException("NOPE.NS", Reason.UNKNOWN)).when(watchlists).addItem(3L, 1L, "NOPE.NS");

        mvc.perform(put("/api/v1/accounts/3/watchlists/77/items/ITC.NS"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"errorCode\":\"WCH-404\",\"message\":\"Watchlist not found\"}", true));
        mvc.perform(put("/api/v1/accounts/3/watchlists/1/items/NOPE.NS"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INS-404"));
    }

    @Test
    @DisplayName("another account is 403 ACC-403, watchlists and alerts alike")
    void anotherAccount() throws Exception {
        when(watchlists.list(anyLong())).thenThrow(new AccountNotReachableException());
        when(alerts.list(anyLong())).thenThrow(new AccountNotReachableException());

        mvc.perform(get("/api/v1/accounts/4/watchlists"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ACC-403"));
        mvc.perform(get("/api/v1/accounts/4/alerts"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ACC-403"));
    }

    @Test
    @DisplayName("sets an alert: 201 ACTIVE, in the contract's shape")
    void createsAlert() throws Exception {
        when(alerts.create(eq(3L), any())).thenReturn(new PriceAlert(5L, "ITC.NS", AlertDirection.ABOVE,
                new BigDecimal("270.0000"), AlertStatus.ACTIVE, Instant.parse("2026-10-06T10:00:00Z"), null, null, null));

        mvc.perform(post("/api/v1/accounts/3/alerts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"ITC.NS\",\"direction\":\"ABOVE\",\"threshold\":270}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"id":5,"symbol":"ITC.NS","direction":"ABOVE","threshold":270.0000,"status":"ACTIVE",
                         "createdAt":"2026-10-06T10:00:00Z","triggeredAt":null,"triggeredPrice":null,
                         "notificationId":null}""", true));
    }

    @Test
    @DisplayName("a threshold of nothing, a direction it does not know, or a missing symbol is VAL-422, and nothing is set")
    void invalidAlert() throws Exception {
        for (String body : new String[] {
                "{\"symbol\":\"ITC.NS\",\"direction\":\"ABOVE\",\"threshold\":0}",
                "{\"symbol\":\"ITC.NS\",\"direction\":\"ABOVE\",\"threshold\":-5}",
                "{\"symbol\":\"ITC.NS\",\"direction\":\"SIDEWAYS\",\"threshold\":270}",
                "{\"direction\":\"ABOVE\",\"threshold\":270}"}) {
            mvc.perform(post("/api/v1/accounts/3/alerts").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        }
        verifyNoInteractions(alerts);
    }

    @Test
    @DisplayName("an alert on a fund is 422 VAL-422, saying why")
    void fund() throws Exception {
        when(alerts.create(eq(3L), any())).thenThrow(new FundAlertException());

        mvc.perform(post("/api/v1/accounts/3/alerts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"120716\",\"direction\":\"ABOVE\",\"threshold\":100}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().json("{\"errorCode\":\"VAL-422\",\"message\":\"A fund has no live price to set an alert on\"}", true));
    }

    @Test
    @DisplayName("cancelling is 204; re-arming answers the alert ACTIVE again")
    void cancelAndRearm() throws Exception {
        when(alerts.rearm(3L, 5L)).thenReturn(new PriceAlert(5L, "ITC.NS", AlertDirection.BELOW, BigDecimal.TEN,
                AlertStatus.ACTIVE, Instant.parse("2026-10-06T10:00:00Z"), null, null, null));

        mvc.perform(delete("/api/v1/accounts/3/alerts/5")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/accounts/3/alerts/5/rearm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        verify(alerts).cancel(3L, 5L);
    }

    @Test
    @DisplayName("no route here fires an alert or delivers anything: a triggered alert's notification id is only read")
    void triggeredIsReadOnly() throws Exception {
        UUID queued = UUID.randomUUID();
        when(alerts.list(3L)).thenReturn(List.of(new PriceAlert(5L, "ITC.NS", AlertDirection.ABOVE, new BigDecimal("270"),
                AlertStatus.TRIGGERED, Instant.parse("2026-10-06T10:00:00Z"), Instant.parse("2026-10-06T10:05:00Z"),
                new BigDecimal("271.10"), queued)));

        mvc.perform(get("/api/v1/accounts/3/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("TRIGGERED"))
                .andExpect(jsonPath("$[0].notificationId").value(queued.toString()));
    }
}
