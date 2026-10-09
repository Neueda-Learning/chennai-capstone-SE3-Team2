package com.yellow.trade.marketdata;

import com.yellow.enums.Reason;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.TradableInstrumentRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily candles for a stock's chart. A daily candle does not move inside a
 * day, so a chart is kept for hours, the most recently used couple of hundred
 * of them; Fauxnance down, the last chart we had is served.
 */
@Service
public class CandleService {

    private static final Logger log = LoggerFactory.getLogger(CandleService.class);

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    /** Five years of one stock is about 1,250 candles; this bounds the memory charts can take. */
    static final int MAX_CHARTS = 200;

    private record Cached(List<Candle> candles, Instant fetchedAt) {
    }

    private final InstrumentMapper instruments;
    private final FauxnanceMarketClient fauxnance;
    private final FauxnanceBudget budget;
    private final MarketDataProperties properties;
    private final Clock clock;
    private final Map<String, Cached> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                    return size() > MAX_CHARTS;
                }
            });

    public CandleService(InstrumentMapper instruments, FauxnanceMarketClient fauxnance, FauxnanceBudget budget,
                         MarketDataProperties properties, Clock clock) {
        this.instruments = instruments;
        this.fauxnance = fauxnance;
        this.budget = budget;
        this.properties = properties;
        this.clock = clock;
    }

    public List<Candle> candles(String symbol, ChartRange range) {
        List<TradableInstrumentRow> listed = instruments.findBySymbols(List.of(symbol));
        if (listed.isEmpty()) {
            throw new InstrumentNotFoundException(symbol, Reason.UNKNOWN);
        }
        if ("MF".equals(listed.get(0).getInstrumentType())) {
            throw new ChartUnavailableException(symbol);
        }

        String key = symbol + "|" + range.label();
        Instant now = clock.instant();
        Cached cached = cache.get(key);
        if (cached != null && cached.fetchedAt().plus(properties.candleTtl()).isAfter(now)) {
            return cached.candles();
        }
        if (!properties.hasFauxnanceKey() || !budget.tryTake()) {
            if (cached != null) {
                return cached.candles();
            }
            throw new PricingUnavailableException("no Fauxnance request available for a chart of " + symbol);
        }

        try {
            LocalDate today = LocalDate.ofInstant(now, INDIA);
            List<Candle> fetched = List.copyOf(fauxnance.candles(symbol, range.from(today), today));
            cache.put(key, new Cached(fetched, now));
            return fetched;
        } catch (PricingUnavailableException e) {
            if (cached != null) {
                log.warn("serving an old {} chart of {}: {}", range.label(), symbol, e.getMessage());
                return cached.candles();
            }
            throw e;
        }
    }
}
