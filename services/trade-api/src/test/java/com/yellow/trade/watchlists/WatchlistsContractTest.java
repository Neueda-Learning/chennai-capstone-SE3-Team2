package com.yellow.trade.watchlists;

import com.yellow.trade.modules.ModuleContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

/** openapi/watchlists.yaml, written before the controllers, is what the code is held to. */
class WatchlistsContractTest {

    private static ModuleContract contract() throws IOException {
        return ModuleContract.of("openapi/watchlists.yaml");
    }

    @Test
    @DisplayName("describes exactly the routes the two controllers serve")
    void routes() throws IOException {
        assertThat(contract().describedRoutes(),
                is(ModuleContract.servedRoutes(WatchlistsController.class, AlertsController.class)));
    }

    @Test
    @DisplayName("each schema's properties are the record's fields")
    void schemas() throws IOException {
        assertThat(contract().properties("Watchlist"), is(ModuleContract.fields(Watchlist.class)));
        assertThat(contract().properties("WatchlistItem"), is(ModuleContract.fields(WatchlistItem.class)));
        assertThat(contract().properties("WatchlistRequest"), is(ModuleContract.fields(WatchlistRequest.class)));
        assertThat(contract().properties("PriceAlert"), is(ModuleContract.fields(PriceAlert.class)));
        assertThat(contract().properties("AlertRequest"), is(ModuleContract.fields(AlertRequest.class)));
    }

    @Test
    @DisplayName("the directions and the states are the ones the code knows")
    void enums() {
        assertThat(names(AlertDirection.values()), is(Set.of("ABOVE", "BELOW")));
        assertThat(names(AlertStatus.values()), is(Set.of("ACTIVE", "TRIGGERED", "CANCELLED")));
    }

    @Test
    @DisplayName("the two codes this module adds are in the file's catalogue")
    void errorCodes() throws IOException {
        assertThat(contract().errorCodes(), hasItems(WatchExceptions.NotFoundException.CODE,
                WatchExceptions.LimitReachedException.CODE));
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
    }
}
