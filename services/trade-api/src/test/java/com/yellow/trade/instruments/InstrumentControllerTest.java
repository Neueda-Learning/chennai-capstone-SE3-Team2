package com.yellow.trade.instruments;

import com.yellow.trade.controllers.GlobalExceptionHandler;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.TradableInstrumentRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstrumentController.class)
@Import(GlobalExceptionHandler.class)
class InstrumentControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private InstrumentMapper instruments;

    private static TradableInstrumentRow row(String symbol, String name, String type, String exchange) {
        TradableInstrumentRow row = new TradableInstrumentRow();
        row.setSymbol(symbol);
        row.setName(name);
        row.setInstrumentType(type);
        row.setExchangeCode(exchange);
        return row;
    }

    @Test
    @DisplayName("Lists each tradable instrument with its symbol, name, type and exchange; a fund has none")
    void lists() throws Exception {
        when(instruments.findTradable()).thenReturn(List.of(
                row("ITC.NS", "ITC Ltd", "STOCK", "NSE"),
                row("SCH100001", "Bluechip Growth Fund", "MF", null)));

        mvc.perform(get("/api/v1/instruments"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"symbol":"ITC.NS","name":"ITC Ltd","type":"STOCK","exchange":"NSE"},
                         {"symbol":"SCH100001","name":"Bluechip Growth Fund","type":"MF","exchange":null}]""", true));
    }
}
