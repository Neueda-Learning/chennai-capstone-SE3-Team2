package com.yellow.trade.marketdata;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Prices and charts for the screens. Public market data, so any signed-in
 * customer may read any instrument's; there is no account in these paths to
 * compare the token against. Under /api/v1/ all the same: the browser reaches
 * Fauxnance only through here, and here only with a token.
 */
@RestController
@Validated
public class MarketDataController {

    private final PriceService prices;
    private final CandleService candles;

    public MarketDataController(PriceService prices, CandleService candles) {
        this.prices = prices;
        this.candles = candles;
    }

    @GetMapping("/api/v1/quotes")
    public List<PriceQuote> quotes(@RequestParam @Size(min = 1, max = 50) List<@Size(max = 30) String> symbols) {
        return prices.quotes(symbols);
    }

    @GetMapping("/api/v1/instruments/{symbol}/candles")
    public CandleSeries candles(@PathVariable @Size(max = 30) String symbol,
                                @RequestParam(defaultValue = "6M") String range) {
        ChartRange chartRange = ChartRange.of(range)
                .orElseThrow(() -> new ConstraintViolationException("unknown chart range " + range, Set.of()));
        return new CandleSeries(symbol, chartRange.label(), candles.candles(symbol, chartRange));
    }
}
