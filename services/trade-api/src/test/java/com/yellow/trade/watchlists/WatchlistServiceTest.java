package com.yellow.trade.watchlists;

import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.security.AccountAccess;
import com.yellow.trade.security.AccountNotReachableException;
import com.yellow.trade.watchlists.WatchExceptions.LimitReachedException;
import com.yellow.trade.watchlists.WatchExceptions.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private WatchlistMapper watchlists;
    @Mock private InstrumentMapper instruments;
    @Mock private AccountAccess access;

    private WatchlistService service() {
        return new WatchlistService(watchlists, instruments, access);
    }

    private static WatchlistRow list(long id, String name, int position) {
        WatchlistRow row = new WatchlistRow();
        row.setWatchlistId(id);
        row.setClientId(3L);
        row.setName(name);
        row.setPosition(position);
        return row;
    }

    private static InstrumentRow instrument(long id, String symbol, String type, boolean tradable) {
        InstrumentRow row = new InstrumentRow();
        row.setInstrumentId(id);
        row.setSymbol(symbol);
        row.setInstrumentType(type);
        row.setTradable(tradable);
        return row;
    }

    @Test
    @DisplayName("lists the account's watchlists in order, each entry with the latest price the stream carried")
    void lists() {
        ItemRow item = new ItemRow();
        item.setWatchlistId(1L);
        item.setSymbol("ITC.NS");
        item.setPosition(1);
        item.setLastPrice(new BigDecimal("266.7"));
        item.setPriceAsOf(Instant.parse("2026-10-06T09:45:00Z"));
        when(watchlists.findForClient(3L)).thenReturn(List.of(list(1L, "Long term", 1), list(2L, "Banks", 2)));
        when(watchlists.findItemsForClient(3L)).thenReturn(List.of(item));

        List<Watchlist> shown = service().list(3L);

        assertThat(shown.size(), is(2));
        assertThat(shown.get(0).items().get(0).symbol(), is("ITC.NS"));
        assertThat(shown.get(0).items().get(0).lastPrice(), is(new BigDecimal("266.7")));
        assertThat(shown.get(1).items().isEmpty(), is(true));
    }

    @Test
    @DisplayName("another account is ACC-403 before anything is read or written")
    void anotherAccount() {
        doThrow(new AccountNotReachableException()).when(access).requireOwn(4L);

        assertThrows(AccountNotReachableException.class, () -> service().create(4L, "Mine now"));

        verifyNoInteractions(watchlists);
    }

    @Test
    @DisplayName("a sixth watchlist is LIM-409, decided under the account's lock")
    void watchlistCap() {
        when(watchlists.countForClient(3L)).thenReturn(WatchLimits.WATCHLISTS);

        assertThrows(LimitReachedException.class, () -> service().create(3L, "Sixth"));

        InOrder order = inOrder(watchlists);
        order.verify(watchlists).lockAccount(3L);
        order.verify(watchlists).countForClient(3L);
        verify(watchlists, never()).insert(any());
    }

    @Test
    @DisplayName("a name is trimmed before it is kept")
    void createTrims() {
        when(watchlists.countForClient(3L)).thenReturn(0);
        when(watchlists.insert(any())).thenAnswer(call -> {
            WatchlistRow row = call.getArgument(0);
            row.setWatchlistId(9L);
            return 1;
        });
        when(watchlists.findOwned(3L, 9L)).thenReturn(list(9L, "Banks", 1));

        Watchlist created = service().create(3L, "  Banks ");

        assertThat(created.name(), is("Banks"));
        assertThat(created.items().isEmpty(), is(true));
    }

    @Test
    @DisplayName("another account's watchlist is WCH-404, the same answer as one that does not exist")
    void notOwned() {
        when(watchlists.findOwned(3L, 77L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service().addItem(3L, 77L, "ITC.NS"));
        assertThrows(NotFoundException.class, () -> service().rename(3L, 77L, "x"));
        verify(watchlists, never()).insertItem(anyLong(), anyLong());
    }

    @Test
    @DisplayName("an instrument nobody lists is INS-404")
    void unknownInstrument() {
        when(watchlists.findOwned(3L, 1L)).thenReturn(list(1L, "Long term", 1));
        when(instruments.findBySymbol("NOPE.NS")).thenReturn(null);

        assertThrows(InstrumentNotFoundException.class, () -> service().addItem(3L, 1L, "NOPE.NS"));
    }

    @Test
    @DisplayName("a 51st entry is LIM-409; one already there changes nothing and is not counted again")
    void itemCap() {
        when(watchlists.findOwned(3L, 1L)).thenReturn(list(1L, "Long term", 1));
        when(instruments.findBySymbol("ITC.NS")).thenReturn(instrument(42L, "ITC.NS", "STOCK", true));
        when(watchlists.hasItem(1L, 42L)).thenReturn(false);
        when(watchlists.countItems(1L)).thenReturn(WatchLimits.ITEMS_PER_WATCHLIST);

        assertThrows(LimitReachedException.class, () -> service().addItem(3L, 1L, "ITC.NS"));

        when(watchlists.hasItem(1L, 42L)).thenReturn(true);
        service().addItem(3L, 1L, "ITC.NS");
        verify(watchlists, never()).insertItem(anyLong(), anyLong());
    }

    @Test
    @DisplayName("a fund can be watched: a watchlist entry is not a position, and needs no stream")
    void fundsCanBeWatched() {
        when(watchlists.findOwned(3L, 1L)).thenReturn(list(1L, "Funds", 1));
        when(instruments.findBySymbol("120716")).thenReturn(instrument(7L, "120716", "MF", true));
        when(watchlists.countItems(1L)).thenReturn(0);

        service().addItem(3L, 1L, "120716");

        verify(watchlists).insertItem(1L, 7L);
    }
}
