package com.yellow.trade.strategy;

import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.marketdata.CandleService;
import com.yellow.trade.marketdata.ChartRange;
import com.yellow.trade.marketdata.ChartUnavailableException;
import com.yellow.trade.marketdata.PricingUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An instrument's indicator view at a quote: its daily candles from the platform's
 * CandleService (kept six hours, so a quote costs no price-service call), and the quote's price
 * as today's close. The last view of each instrument is kept, so listing strategies shows what
 * they wait on without reading anything.
 */
@Component
class IndicatorReader {

    private static final Logger log = LoggerFactory.getLogger(IndicatorReader.class);
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CandleService candles;
    private final Map<String, Indicators.View> latest = new ConcurrentHashMap<>();

    IndicatorReader(CandleService candles) {
        this.candles = candles;
    }

    /** The view at this quote; empty when there are no candles to read, and the strategies wait for a later quote. */
    Optional<Indicators.View> view(String symbol, StrategyQuote quote) {
        try {
            Indicators.View view = Indicators.read(candles.candles(symbol, ChartRange.SIX_MONTHS), quote.price(),
                    quote.quoteAsOf(), LocalDate.ofInstant(quote.quoteAsOf(), IST));
            latest.put(symbol, view);
            return Optional.of(view);
        } catch (PricingUnavailableException | ChartUnavailableException | InstrumentNotFoundException e) {
            log.warn("no indicator view of {} at quote {}: {}", symbol, quote.eventId(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** The last view read for the instrument, if any quote has been seen since the service started. */
    Optional<Indicators.View> latest(String symbol) {
        return Optional.ofNullable(latest.get(symbol));
    }
}
