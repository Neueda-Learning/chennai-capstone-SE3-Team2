package com.yellow.trade.instruments;

import com.yellow.trade.mappers.InstrumentMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The instruments a customer may order, so the order ticket offers a list
 * rather than a blank box. Under /api/v1/, so it needs a token like every
 * other route; not in contracts/trade-api.yaml -- described in our own
 * extension contract.
 */
@RestController
public class InstrumentController {

    private final InstrumentMapper instruments;

    public InstrumentController(InstrumentMapper instruments) {
        this.instruments = instruments;
    }

    /** Tradable instruments, by symbol. */
    @GetMapping("/api/v1/instruments")
    @Transactional(readOnly = true)
    public List<InstrumentResponse> tradable() {
        return instruments.findTradable().stream()
                .map(row -> new InstrumentResponse(row.getSymbol(), row.getName(),
                        row.getInstrumentType(), row.getExchangeCode()))
                .toList();
    }
}
