package com.yellow.trade.advice;

import com.yellow.enums.Reason;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.marketdata.Candle;
import com.yellow.trade.marketdata.CandleService;
import com.yellow.trade.marketdata.ChartRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Signals, computed from real daily candles and kept. The candles come from
 * the platform's CandleService, which keeps a chart six hours (a daily candle
 * does not move inside a day). A signal is computed the first time anyone
 * asks, then recomputed on a timer (refreshAll), never per quote: a quote
 * moves the latest price only, and the next pass takes it in.
 *
 * Market data is public, so any signed-in customer may read any stock's
 * signal; there is no account to compare. A fund has no candles here: 422.
 */
@Service
public class AdviceService {

    private static final Logger log = LoggerFactory.getLogger(AdviceService.class);

    static final String DISCLAIMER = "Information, not advice. Computed from delayed educational data; "
            + "past prices do not predict future ones.";
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    /** A symbol nobody has asked about for a day is no longer refreshed. */
    static final Duration FORGET_AFTER = Duration.ofDays(1);
    /** The most signals kept; past it, the one asked about longest ago goes. */
    static final int MAX_SIGNALS = 500;

    private record Kept(Signal signal, Instant lastAsked) {
    }

    private final InstrumentMapper instruments;
    private final CandleService candles;
    private final LatestPrices prices;
    private final Clock clock;
    private final Duration refresh;
    private final Map<String, Kept> kept = new ConcurrentHashMap<>();

    public AdviceService(InstrumentMapper instruments, CandleService candles, LatestPrices prices, Clock clock,
                         @Value("${advice.refresh.interval-ms:300000}") long refreshMs) {
        this.instruments = instruments;
        this.candles = candles;
        this.prices = prices;
        this.clock = clock;
        this.refresh = Duration.ofMillis(refreshMs);
    }

    public Signal signal(String symbol) {
        InstrumentRow instrument = instruments.findBySymbol(symbol);
        if (instrument == null) {
            throw new InstrumentNotFoundException(symbol, Reason.UNKNOWN);
        }
        if ("MF".equals(instrument.getInstrumentType())) {
            throw new AdviceExceptions.FundSignalException();
        }
        Instant now = clock.instant();
        Kept held = kept.get(symbol);
        // The timer keeps it fresh; if the timer has not run for two of its
        // intervals, the route computes it rather than serve one that old.
        if (held != null && held.signal().computedAt().plus(refresh.multipliedBy(2)).isAfter(now)) {
            kept.put(symbol, new Kept(held.signal(), now));
            return held.signal();
        }
        Signal signal = compute(symbol, now);
        keep(symbol, signal, now);
        return signal;
    }

    /** One pass of the timer: every signal asked for in the last day, recomputed; the rest forgotten. */
    public void refreshAll() {
        Instant now = clock.instant();
        kept.entrySet().removeIf(entry -> entry.getValue().lastAsked().plus(FORGET_AFTER).isBefore(now));
        for (Map.Entry<String, Kept> entry : kept.entrySet()) {
            try {
                kept.put(entry.getKey(), new Kept(compute(entry.getKey(), now), entry.getValue().lastAsked()));
            } catch (RuntimeException e) {
                // Kept as it was: an old signal says when it was computed.
                log.warn("signal for {} not recomputed: {}", entry.getKey(), e.getClass().getSimpleName());
            }
        }
    }

    private Signal compute(String symbol, Instant now) {
        List<Candle> daily = new ArrayList<>(candles.candles(symbol, ChartRange.SIX_MONTHS));
        daily.sort(Comparator.comparing(Candle::date));
        List<BigDecimal> closes = new ArrayList<>(daily.stream().map(Candle::close).toList());

        Optional<LatestPrices.Price> latest = prices.of(symbol);
        LocalDate lastDay = daily.isEmpty() ? null : daily.get(daily.size() - 1).date();
        // Today's price, while today has no candle yet, counts as today's close.
        latest.filter(p -> lastDay == null || LocalDate.ofInstant(p.asOf(), IST).isAfter(lastDay))
                .ifPresent(p -> closes.add(p.price()));

        Methodology.Reading reading = Methodology.read(closes);
        BigDecimal lastPrice = latest.map(LatestPrices.Price::price)
                .orElse(closes.isEmpty() ? null : closes.get(closes.size() - 1));
        SignalFigures figures = new SignalFigures(Methodology.figure(reading.sma20(), 4),
                Methodology.figure(reading.sma50(), 4), Methodology.figure(reading.rsi14(), 2), lastPrice,
                latest.map(LatestPrices.Price::asOf).orElse(null), reading.days());
        return new Signal(symbol, reading.direction(), reading.strength(), Methodology.NAME, reading.reason(), figures,
                now, DISCLAIMER);
    }

    private void keep(String symbol, Signal signal, Instant now) {
        kept.put(symbol, new Kept(signal, now));
        if (kept.size() > MAX_SIGNALS) {
            kept.entrySet().stream().min(Comparator.comparing(entry -> entry.getValue().lastAsked()))
                    .ifPresent(oldest -> kept.remove(oldest.getKey()));
        }
    }
}
