package com.yellow.trade.advice;

import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.marketdata.ChartUnavailableException;
import com.yellow.trade.marketdata.PricingUnavailableException;
import com.yellow.trade.portfolio.api.Holdings;
import com.yellow.trade.security.AccountAccess;
import com.yellow.trade.watchlists.api.WatchedInstruments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Signals for what one customer holds and watches: the holdings from the
 * portfolio module, the watchlists from the watchlists module, each through
 * its published api, and every signal from AdviceService, kept and refreshed
 * as any other. The customer's own account only (ACC-403).
 *
 * A stock with no view to give is listed with no signal and the reason: a
 * fund (no daily candles), too little history, prices that cannot be read
 * just now, or no longer traded. One stock's trouble never fails the list.
 *
 * At most MAX_STOCKS, holdings first: each new stock costs a price-service
 * call for its candles, kept six hours, from the platform's daily budget.
 */
@Service
public class AccountAdviceService {

    private static final Logger log = LoggerFactory.getLogger(AccountAdviceService.class);

    static final int MAX_STOCKS = 30;
    static final String FUND = "A fund is priced once a day at its NAV, and this method needs daily candles, so there is no signal.";
    static final String UNREADABLE = "Its prices could not be read just now, so there is no signal; it is tried again on the next read.";
    static final String NOT_TRADED = "It is no longer traded, so there is no signal.";

    private final AdviceService advice;
    private final Holdings holdings;
    private final WatchedInstruments watched;
    private final InstrumentMapper instruments;
    private final AccountAccess access;
    private final Clock clock;

    public AccountAdviceService(AdviceService advice, Holdings holdings, WatchedInstruments watched,
                                InstrumentMapper instruments, AccountAccess access, Clock clock) {
        this.advice = advice;
        this.holdings = holdings;
        this.watched = watched;
        this.instruments = instruments;
        this.access = access;
        this.clock = clock;
    }

    public AccountAdvice forAccount(long accountId) {
        access.requireOwn(accountId);
        List<String> held = holdings.heldSymbols(accountId);
        List<String> watching = watched.watchedSymbols(accountId);
        Set<String> symbols = new LinkedHashSet<>(held);
        symbols.addAll(watching);
        Set<String> heldSet = Set.copyOf(held);
        Set<String> watchedSet = Set.copyOf(watching);
        Instant now = clock.instant();
        List<AdviceItem> items = symbols.stream()
                .limit(MAX_STOCKS)
                .map(symbol -> new AdviceItem(symbol, heldSet.contains(symbol), watchedSet.contains(symbol), signalFor(symbol, now)))
                .toList();
        return new AccountAdvice(accountId, items, symbols.size() > MAX_STOCKS, AdviceService.DISCLAIMER);
    }

    private Signal signalFor(String symbol, Instant now) {
        InstrumentRow instrument = instruments.findBySymbol(symbol);
        if (instrument == null) {
            return none(symbol, NOT_TRADED, now);
        }
        if ("MF".equals(instrument.getInstrumentType())) {
            return none(symbol, FUND, now);
        }
        try {
            return advice.signal(symbol);
        } catch (PricingUnavailableException | ChartUnavailableException e) {
            log.warn("no signal for {}: {}", symbol, e.getClass().getSimpleName());
            return none(symbol, UNREADABLE, now);
        } catch (InstrumentNotFoundException e) {
            return none(symbol, NOT_TRADED, now);
        }
    }

    private static Signal none(String symbol, String reason, Instant now) {
        return new Signal(symbol, null, null, Methodology.NAME, reason, null, now, AdviceService.DISCLAIMER);
    }
}
