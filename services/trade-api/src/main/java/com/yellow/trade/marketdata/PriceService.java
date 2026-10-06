package com.yellow.trade.marketdata;

import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.TradableInstrumentRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The latest price of any instrument we list: stocks from Fauxnance, funds
 * from the MF NAV service, each batched 25 to a request and kept for a while,
 * so a screen re-reading every few seconds costs the quota nothing.
 *
 * When a source fails or the day's budget is spent, the last price we had is
 * served marked stale: an old price shown as old beats a blank.
 */
@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    private record Cached(PriceQuote quote, Instant fetchedAt) {
    }

    private record Listed(List<String> stocks, List<String> funds) {
        Set<String> all() {
            Set<String> all = new java.util.HashSet<>(stocks);
            all.addAll(funds);
            return all;
        }
    }

    private final InstrumentMapper instruments;
    private final FauxnanceMarketClient fauxnance;
    private final MfNavMarketClient nav;
    private final FauxnanceBudget budget;
    private final MarketDataProperties properties;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public PriceService(InstrumentMapper instruments, FauxnanceMarketClient fauxnance, MfNavMarketClient nav,
                        FauxnanceBudget budget, MarketDataProperties properties, Clock clock) {
        this.instruments = instruments;
        this.fauxnance = fauxnance;
        this.nav = nav;
        this.budget = budget;
        this.properties = properties;
        this.clock = clock;
    }

    /** The priced ones among these symbols. One we do not list, or cannot price, is absent. */
    public Map<String, PriceQuote> latest(Collection<String> symbols) {
        return price(listed(symbols));
    }

    /**
     * Every listed symbol among these, in the order asked: priced, or
     * {@link PriceQuote#unpriced} when no price could be had.
     */
    public List<PriceQuote> quotes(List<String> symbols) {
        Listed listed = listed(symbols);
        Map<String, PriceQuote> priced = price(listed);
        Set<String> known = listed.all();
        return symbols.stream().distinct().filter(known::contains)
                .map(s -> priced.getOrDefault(s, PriceQuote.unpriced(s)))
                .toList();
    }

    private Listed listed(Collection<String> symbols) {
        List<String> distinct = symbols.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return new Listed(List.of(), List.of());
        }
        Map<Boolean, List<String>> byFund = instruments.findBySymbols(distinct).stream()
                .collect(Collectors.partitioningBy(row -> "MF".equals(row.getInstrumentType()),
                        Collectors.mapping(TradableInstrumentRow::getSymbol, Collectors.toList())));
        return new Listed(byFund.get(false), byFund.get(true));
    }

    private Map<String, PriceQuote> price(Listed listed) {
        Map<String, PriceQuote> out = new LinkedHashMap<>();
        refresh(listed.stocks(), properties.quoteTtl(), this::fetchStocks, out);
        refresh(listed.funds(), properties.navTtl(), nav::navs, out);
        return out;
    }

    private void refresh(List<String> symbols, Duration ttl, Function<List<String>, Map<String, PriceQuote>> fetch,
                         Map<String, PriceQuote> out) {
        Instant now = clock.instant();
        List<String> due = new ArrayList<>();
        for (String symbol : symbols) {
            Cached cached = cache.get(symbol);
            if (cached != null && cached.fetchedAt().plus(ttl).isAfter(now)) {
                out.put(symbol, cached.quote());
            } else {
                due.add(symbol);
            }
        }

        for (int start = 0; start < due.size(); start += FauxnanceMarketClient.MAX_BATCH) {
            List<String> batch = due.subList(start, Math.min(start + FauxnanceMarketClient.MAX_BATCH, due.size()));
            Map<String, PriceQuote> fetched;
            try {
                fetched = fetch.apply(batch);
            } catch (PricingUnavailableException e) {
                log.warn("no fresh prices for {} symbol(s): {}", batch.size(), e.getMessage());
                fetched = Map.of();
            }
            for (String symbol : batch) {
                PriceQuote quote = fetched.get(symbol);
                if (quote != null) {
                    cache.put(symbol, new Cached(quote, now));
                    out.put(symbol, quote);
                } else if (cache.containsKey(symbol)) {
                    out.put(symbol, cache.get(symbol).quote().markedStale());
                }
            }
        }
    }

    private Map<String, PriceQuote> fetchStocks(List<String> batch) {
        if (!properties.hasFauxnanceKey()) {
            return Map.of();
        }
        if (!budget.tryTake()) {
            log.warn("today's Fauxnance budget of {} is spent: serving the last prices we have",
                    properties.fauxnanceDailyBudget());
            return Map.of();
        }
        return fauxnance.quotes(batch);
    }
}
