package com.yellow.trade.preferences;

import com.yellow.trade.modules.ModuleContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** openapi/preferences.yaml, written before the controller, is what the code is held to. */
class PreferencesContractTest {

    private static ModuleContract contract() throws IOException {
        return ModuleContract.of("openapi/preferences.yaml");
    }

    @Test
    @DisplayName("describes exactly the routes the controller serves")
    void routes() throws IOException {
        assertThat(contract().describedRoutes(), is(ModuleContract.servedRoutes(PreferencesController.class)));
    }

    @Test
    @DisplayName("each schema's properties are the record's fields")
    void schemas() throws IOException {
        assertThat(contract().properties("Preferences"), is(ModuleContract.fields(Preferences.class)));
        assertThat(contract().properties("PreferencesUpdate"), is(ModuleContract.fields(PreferencesUpdate.class)));
    }

    @Test
    @DisplayName("the landing screens are the ones the code knows, spelled as the URL spells them")
    void landingScreens() {
        Set<String> values = Arrays.stream(LandingScreen.values()).map(LandingScreen::value)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(values, is(new TreeSet<>(Set.of("dashboard", "orders", "holdings", "market-watch"))));
    }
}
