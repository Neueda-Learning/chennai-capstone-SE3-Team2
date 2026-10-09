package com.yellow.trade.instruments;

import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.TradableInstrumentRow;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Finding an instrument: by the text a customer types, by the symbols a
 * screen already holds, or the whole tradable list. Under /api/v1/, so it
 * needs a token like every other route; not in Contracts/API Schemas/trade-api.yaml --
 * described in our own extension contract.
 */
@RestController
@Validated
public class InstrumentController {

    private final InstrumentMapper instruments;

    public InstrumentController(InstrumentMapper instruments) {
        this.instruments = instruments;
    }

    /**
     * One mode per request: q searches, symbols looks up, neither lists every
     * tradable instrument. Both at once is refused rather than one silently
     * winning.
     */
    @GetMapping("/api/v1/instruments")
    @Transactional(readOnly = true)
    public List<InstrumentResponse> find(
            @RequestParam(required = false) @Size(min = 1, max = 50) String q,
            @RequestParam(required = false) @Pattern(regexp = "STOCK|ETF|MF") String type,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) @Size(min = 1, max = 50) List<@Size(max = 30) String> symbols) {

        if (q != null && symbols != null) {
            throw new ConstraintViolationException("q and symbols cannot be combined", Set.of());
        }

        List<TradableInstrumentRow> rows;
        if (symbols != null) {
            rows = instruments.findBySymbols(symbols);
        } else if (q != null) {
            String text = q.strip();
            if (text.isEmpty()) {
                // A blank search would match every name: refuse it rather
                // than answer with the first twenty of four thousand.
                throw new ConstraintViolationException("q is blank", Set.of());
            }
            rows = instruments.search(text, type, limit);
        } else {
            rows = instruments.findTradable(type);
        }
        return rows.stream().map(InstrumentResponse::from).toList();
    }
}
