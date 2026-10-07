package com.yellow.trade.strategy;

import com.yellow.trade.modules.ModuleContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** openapi/strategy.yaml, written before the controller, is what the code is held to. */
class StrategyContractTest {

    private static ModuleContract contract() throws IOException {
        return ModuleContract.of("openapi/strategy.yaml");
    }

    @Test
    @DisplayName("describes exactly the routes the controller serves")
    void routes() throws IOException {
        assertThat(contract().describedRoutes(), is(ModuleContract.servedRoutes(StrategyController.class)));
    }

    @Test
    @DisplayName("each schema's properties are the record's fields")
    void schemas() throws IOException {
        assertThat(contract().properties("Strategy"), is(ModuleContract.fields(Strategy.class)));
        assertThat(contract().properties("StrategyRequest"), is(ModuleContract.fields(StrategyRequest.class)));
        assertThat(contract().properties("EnabledRequest"), is(ModuleContract.fields(EnabledRequest.class)));
        assertThat(contract().properties("StrategyRun"), is(ModuleContract.fields(StrategyRun.class)));
    }

    @Test
    @DisplayName("the triggers, states and outcomes are the ones the code and the tables know")
    void enums() {
        assertThat(names(Trigger.values()), is(Set.of("FALLS_THROUGH", "RISES_THROUGH")));
        assertThat(names(StrategyStatus.values()), is(Set.of("ARMED", "FIRED", "STOPPED")));
        assertThat(names(RunOutcome.values()), is(Set.of("PLACED", "FILLED", "REJECTED", "REFUSED_LIMIT", "FAILED", "STOPPED")));
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }
}
