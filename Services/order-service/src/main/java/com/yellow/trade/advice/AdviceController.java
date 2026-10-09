package com.yellow.trade.advice;

import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A stock's signal, as openapi/advice.yaml describes. Under /api/v1/, so only
 * a signed-in customer reads it; market data is public, so there is no
 * account to compare. It informs; nothing here places anything.
 */
@RestController
@RequestMapping("/api/v1/advice")
@Validated
public class AdviceController {

    private final AdviceService advice;

    public AdviceController(AdviceService advice) {
        this.advice = advice;
    }

    @GetMapping("/{symbol}")
    public Signal signal(@PathVariable("symbol") @Size(max = 30) String symbol) {
        return advice.signal(symbol);
    }
}
