package com.yellow.trade.watchlists;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchedInstrumentsReaderTest {

    @Mock private WatchlistMapper watchlists;

    private static ItemRow item(long watchlistId, String symbol, int position) {
        ItemRow row = new ItemRow();
        row.setWatchlistId(watchlistId);
        row.setSymbol(symbol);
        row.setPosition(position);
        return row;
    }

    @Test
    @DisplayName("what an account watches, for advice: every list's entries in list order, each symbol once")
    void watched() {
        when(watchlists.findItemsForClient(3L)).thenReturn(List.of(item(1, "RELIANCE.NS", 1), item(1, "ITC.NS", 2),
                item(2, "ITC.NS", 1), item(2, "SBIN.NS", 2)));

        assertThat(new WatchedInstrumentsReader(watchlists).watchedSymbols(3), contains("RELIANCE.NS", "ITC.NS", "SBIN.NS"));
    }
}
