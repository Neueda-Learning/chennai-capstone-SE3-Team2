package com.yellow.trade.portfolio;

import com.yellow.exceptions.InvalidOrderException;
import com.yellow.trade.PlatformConstants;
import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.mappers.PositionRow;
import com.yellow.trade.marketdata.PriceQuote;
import com.yellow.trade.marketdata.PriceService;
import com.yellow.trade.marketdata.PricingUnavailableException;
import com.yellow.trade.security.AccountAccess;
import com.yellow.trade.services.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The portfolio routes' answers. Every one the caller's own account only,
 * checked before anything is read (ACC-403, logged by AccountAccess).
 *
 * Positions are read where they live, prices through the platform's shared
 * price layer (stocks from Fauxnance, funds at their NAV, batched and cached:
 * decision log 0010), cash through AccountService, the layer that owns it.
 * Realised profit and loss is what RealisedBook booked at each sale. Nothing
 * here writes anything: a pricing outage answers on these routes and leaves
 * order placement alone.
 */
@Service
public class PortfolioService {

    static final String BASE_CURRENCY = PlatformConstants.QUOTE_CURRENCY;
    /** Dates in the routes are Indian dates: the platform trades INR only. */
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Instant EVER_FROM = Instant.EPOCH;
    private static final Instant EVER_UNTIL = Instant.parse("9999-12-31T00:00:00Z");

    private final AccountAccess access;
    private final PositionMapper positions;
    private final PriceService prices;
    private final AccountService accounts;
    private final RealisedMapper realised;
    private final Clock clock;

    public PortfolioService(AccountAccess access, PositionMapper positions, PriceService prices,
                            AccountService accounts, RealisedMapper realised, Clock clock) {
        this.access = access;
        this.positions = positions;
        this.prices = prices;
        this.accounts = accounts;
        this.realised = realised;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PortfolioSummary summary(long accountId) {
        access.requireOwn(accountId);
        Valuation valuation = value(accountId, null);
        BigDecimal cash = Valuation.money(accounts.getBalance(accountId).cashBalance());
        BigDecimal booked = Valuation.money(realised.total(accountId, EVER_FROM, EVER_UNTIL));
        return new PortfolioSummary(accountId, BASE_CURRENCY, cash, valuation.marketValue(), valuation.costBasis(),
                valuation.unrealisedPnl(), valuation.unrealisedPnlPercent(), booked,
                cash.add(valuation.marketValue()), valuation.positions().size(), valuation.partial(), clock.instant());
    }

    /** Every open holding priced; with a symbol, only that one (none if it is not held). */
    @Transactional(readOnly = true)
    public List<PricedPosition> positions(long accountId, String symbol) {
        access.requireOwn(accountId);
        return value(accountId, symbol).positions();
    }

    @Transactional(readOnly = true)
    public PnlResponse pnl(long accountId, LocalDate from, LocalDate to, boolean bySymbol) {
        access.requireOwn(accountId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidOrderException("from", from + " is after " + to);
        }
        Valuation valuation = value(accountId, null);
        Instant start = from == null ? EVER_FROM : from.atStartOfDay(IST).toInstant();
        Instant until = to == null ? EVER_UNTIL : to.plusDays(1).atStartOfDay(IST).toInstant();
        BigDecimal booked = Valuation.money(realised.total(accountId, start, until));
        List<SymbolPnl> breakdown = bySymbol ? breakdown(valuation, realised.bySymbol(accountId, start, until)) : null;
        return new PnlResponse(accountId, BASE_CURRENCY, from, to, booked, valuation.unrealisedPnl(),
                booked.add(valuation.unrealisedPnl()), breakdown, clock.instant());
    }

    /** Priced together, in one call to the price layer. Held, and none priced: MKT-503. */
    private Valuation value(long accountId, String symbol) {
        List<PositionRow> held = positions.findByAccountId(accountId).stream()
                .filter(row -> symbol == null || symbol.equals(row.getSymbol()))
                .toList();
        Map<String, PriceQuote> quotes = held.isEmpty() ? Map.of()
                : prices.latest(held.stream().map(PositionRow::getSymbol).distinct().toList());
        Valuation valuation = Valuation.of(accountId, held, quotes);
        if (valuation.nothingPriced()) {
            throw new PricingUnavailableException("no price for any of the " + held.size() + " holding(s) asked for");
        }
        return valuation;
    }

    private static List<SymbolPnl> breakdown(Valuation valuation, List<SymbolRealised> booked) {
        Map<String, BigDecimal> unrealised = new LinkedHashMap<>();
        for (PricedPosition position : valuation.positions()) {
            unrealised.merge(position.symbol(),
                    position.unrealisedPnl() == null ? BigDecimal.ZERO : position.unrealisedPnl(), BigDecimal::add);
        }
        Map<String, BigDecimal> realisedBySymbol = booked.stream()
                .collect(Collectors.toMap(SymbolRealised::getSymbol, SymbolRealised::getRealised, BigDecimal::add));
        TreeSet<String> symbols = new TreeSet<>(unrealised.keySet());
        symbols.addAll(realisedBySymbol.keySet());
        return symbols.stream().map(symbol -> {
            BigDecimal r = Valuation.money(realisedBySymbol.getOrDefault(symbol, BigDecimal.ZERO));
            BigDecimal u = Valuation.money(unrealised.getOrDefault(symbol, BigDecimal.ZERO));
            return new SymbolPnl(symbol, r, u, r.add(u));
        }).toList();
    }
}
