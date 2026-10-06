package com.yellow.trade.instruments;

import com.yellow.trade.controllers.GlobalExceptionHandler;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.TradableInstrumentRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstrumentController.class)
@Import(GlobalExceptionHandler.class)
class InstrumentControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private InstrumentMapper instruments;

    private static TradableInstrumentRow row(String symbol, String name, String type, String exchange,
                                             boolean tradable) {
        TradableInstrumentRow row = new TradableInstrumentRow();
        row.setSymbol(symbol);
        row.setName(name);
        row.setInstrumentType(type);
        row.setExchangeCode(exchange);
        row.setTradable(tradable);
        return row;
    }

    @Nested
    @DisplayName("with no query")
    class EveryTradable {

        @Test
        @DisplayName("lists each tradable instrument with its symbol, name, type and exchange; a fund has none")
        void lists() throws Exception {
            when(instruments.findTradable(null)).thenReturn(List.of(
                    row("ITC.NS", "ITC Ltd", "STOCK", "NSE", true),
                    row("SCH100001", "Bluechip Growth Fund", "MF", null, true)));

            mvc.perform(get("/api/v1/instruments"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            [{"symbol":"ITC.NS","name":"ITC Ltd","type":"STOCK","exchange":"NSE","tradable":true},
                             {"symbol":"SCH100001","name":"Bluechip Growth Fund","type":"MF","exchange":null,
                              "tradable":true}]""", true));
        }

        @Test
        @DisplayName("narrows the list to one type")
        void byType() throws Exception {
            when(instruments.findTradable("MF")).thenReturn(List.of());

            mvc.perform(get("/api/v1/instruments").param("type", "MF")).andExpect(status().isOk());

            verify(instruments).findTradable("MF");
        }
    }

    @Nested
    @DisplayName("searching with q")
    class Search {

        @Test
        @DisplayName("answers in the mapper's order, trimmed text and 20 results by default")
        void searches() throws Exception {
            when(instruments.search("tata", null, 20)).thenReturn(List.of(
                    row("TATASTEEL.NS", "Tata Steel Limited", "STOCK", "NSE", true),
                    row("147794", "Tata Small Cap Fund - Direct Plan - Growth", "MF", null, true)));

            mvc.perform(get("/api/v1/instruments").param("q", "  tata "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].symbol").value("TATASTEEL.NS"))
                    .andExpect(jsonPath("$[1].symbol").value("147794"));
        }

        @Test
        @DisplayName("passes the type and the limit through")
        void typeAndLimit() throws Exception {
            when(instruments.search("hdfc", "MF", 5)).thenReturn(List.of());

            mvc.perform(get("/api/v1/instruments").param("q", "hdfc").param("type", "MF").param("limit", "5"))
                    .andExpect(status().isOk());

            verify(instruments).search("hdfc", "MF", 5);
        }

        @Test
        @DisplayName("refuses blank text with VAL-422 rather than listing everything")
        void blank() throws Exception {
            mvc.perform(get("/api/v1/instruments").param("q", "   "))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("VAL-422"));

            verify(instruments, never()).search(anyString(), any(), anyInt());
        }

        @Test
        @DisplayName("refuses text over 50 characters")
        void tooLong() throws Exception {
            mvc.perform(get("/api/v1/instruments").param("q", "x".repeat(51)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("refuses a limit outside 1 to 50")
        void limitRange() throws Exception {
            mvc.perform(get("/api/v1/instruments").param("q", "tata").param("limit", "0"))
                    .andExpect(status().isUnprocessableEntity());
            mvc.perform(get("/api/v1/instruments").param("q", "tata").param("limit", "51"))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("refuses a type that is not STOCK, ETF or MF")
        void unknownType() throws Exception {
            mvc.perform(get("/api/v1/instruments").param("q", "tata").param("type", "BOND"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("looking symbols up")
    class Lookup {

        @Test
        @DisplayName("returns the named instruments, a delisted one marked not tradable")
        void looksUp() throws Exception {
            when(instruments.findBySymbols(List.of("MERSTL", "ITC.NS"))).thenReturn(List.of(
                    row("ITC.NS", "ITC Ltd", "STOCK", "NSE", true),
                    row("MERSTL", "Meridian Steel Ltd", "STOCK", "NSE", false)));

            mvc.perform(get("/api/v1/instruments").param("symbols", "MERSTL,ITC.NS"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].symbol").value("MERSTL"))
                    .andExpect(jsonPath("$[1].tradable").value(false));
        }

        @Test
        @DisplayName("refuses more than 50 symbols")
        void tooMany() throws Exception {
            String fiftyOne = String.join(",", java.util.Collections.nCopies(51, "ITC.NS"));

            mvc.perform(get("/api/v1/instruments").param("symbols", fiftyOne))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("refuses q and symbols together: which one was meant is a guess")
        void notBoth() throws Exception {
            mvc.perform(get("/api/v1/instruments").param("q", "itc").param("symbols", "ITC.NS"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("VAL-422"));

            verifyNoInteractions(instruments);
        }
    }
}
