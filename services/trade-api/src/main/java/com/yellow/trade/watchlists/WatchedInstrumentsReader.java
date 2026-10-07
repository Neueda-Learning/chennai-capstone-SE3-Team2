package com.yellow.trade.watchlists;

import com.yellow.trade.watchlists.api.WatchedInstruments;
import org.springframework.stereotype.Component;

import java.util.List;

/** The watchlists module's answer to "what does this account watch". */
@Component
class WatchedInstrumentsReader implements WatchedInstruments {

    private final WatchlistMapper watchlists;

    WatchedInstrumentsReader(WatchlistMapper watchlists) {
        this.watchlists = watchlists;
    }

    @Override
    public List<String> watchedSymbols(long accountId) {
        return watchlists.findItemsForClient(accountId).stream().map(ItemRow::getSymbol).distinct().toList();
    }
}
