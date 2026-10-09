package com.yellow.trade.watchlists;

import com.yellow.enums.Reason;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.security.AccountAccess;
import com.yellow.trade.watchlists.WatchExceptions.LimitReachedException;
import com.yellow.trade.watchlists.WatchExceptions.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A customer's watchlists. Every call is the caller's own account only,
 * checked first (ACC-403); a watchlist is looked up on that account, so
 * another account's is WCH-404. Changes that a cap governs run under the
 * account's lock, so two at once cannot both slip under it.
 */
@Service
public class WatchlistService {

    private final WatchlistMapper watchlists;
    private final InstrumentMapper instruments;
    private final AccountAccess access;

    public WatchlistService(WatchlistMapper watchlists, InstrumentMapper instruments, AccountAccess access) {
        this.watchlists = watchlists;
        this.instruments = instruments;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<Watchlist> list(long accountId) {
        access.requireOwn(accountId);
        Map<Long, List<WatchlistItem>> items = watchlists.findItemsForClient(accountId).stream()
                .collect(Collectors.groupingBy(ItemRow::getWatchlistId,
                        Collectors.mapping(WatchlistService::item, Collectors.toList())));
        return watchlists.findForClient(accountId).stream()
                .map(row -> watchlist(row, items.getOrDefault(row.getWatchlistId(), List.of())))
                .toList();
    }

    @Transactional
    public Watchlist create(long accountId, String name) {
        access.requireOwn(accountId);
        watchlists.lockAccount(accountId);
        if (watchlists.countForClient(accountId) >= WatchLimits.WATCHLISTS) {
            throw new LimitReachedException("At most " + WatchLimits.WATCHLISTS + " watchlists an account");
        }
        WatchlistRow row = new WatchlistRow();
        row.setClientId(accountId);
        row.setName(name.strip());
        watchlists.insert(row);
        return watchlist(owned(accountId, row.getWatchlistId()), List.of());
    }

    @Transactional
    public Watchlist rename(long accountId, long watchlistId, String name) {
        access.requireOwn(accountId);
        owned(accountId, watchlistId);
        watchlists.rename(accountId, watchlistId, name.strip());
        return list(accountId).stream().filter(w -> w.id() == watchlistId).findFirst().orElseThrow();
    }

    @Transactional
    public void delete(long accountId, long watchlistId) {
        access.requireOwn(accountId);
        if (watchlists.delete(accountId, watchlistId) == 0) {
            throw new NotFoundException("Watchlist");
        }
    }

    /** Adds to the end; one already there changes nothing. Anything listed can be watched, held or not. */
    @Transactional
    public void addItem(long accountId, long watchlistId, String symbol) {
        access.requireOwn(accountId);
        owned(accountId, watchlistId);
        InstrumentRow instrument = instruments.findBySymbol(symbol);
        if (instrument == null) {
            throw new InstrumentNotFoundException(symbol, Reason.UNKNOWN);
        }
        watchlists.lockAccount(accountId);
        if (watchlists.hasItem(watchlistId, instrument.getInstrumentId())) {
            return;
        }
        if (watchlists.countItems(watchlistId) >= WatchLimits.ITEMS_PER_WATCHLIST) {
            throw new LimitReachedException("At most " + WatchLimits.ITEMS_PER_WATCHLIST + " instruments a watchlist");
        }
        watchlists.insertItem(watchlistId, instrument.getInstrumentId());
    }

    /** Takes one off; one not there changes nothing. */
    @Transactional
    public void removeItem(long accountId, long watchlistId, String symbol) {
        access.requireOwn(accountId);
        owned(accountId, watchlistId);
        InstrumentRow instrument = instruments.findBySymbol(symbol);
        if (instrument != null) {
            watchlists.deleteItem(watchlistId, instrument.getInstrumentId());
        }
    }

    private WatchlistRow owned(long accountId, long watchlistId) {
        WatchlistRow row = watchlists.findOwned(accountId, watchlistId);
        if (row == null) {
            throw new NotFoundException("Watchlist");
        }
        return row;
    }

    private static Watchlist watchlist(WatchlistRow row, List<WatchlistItem> items) {
        return new Watchlist(row.getWatchlistId(), row.getName(), row.getPosition(), items);
    }

    private static WatchlistItem item(ItemRow row) {
        return new WatchlistItem(row.getSymbol(), row.getPosition(), row.getLastPrice(), row.getChangePercent(),
                row.getPriceAsOf(), row.isStale());
    }
}
