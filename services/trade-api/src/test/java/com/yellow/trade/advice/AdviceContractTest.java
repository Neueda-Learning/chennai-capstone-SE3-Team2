package com.yellow.trade.advice;

import com.yellow.trade.modules.ModuleContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** openapi/advice.yaml, written before the controller, is what the code is held to. */
class AdviceContractTest {

    private static ModuleContract contract() throws IOException {
        return ModuleContract.of("openapi/advice.yaml");
    }

    @Test
    @DisplayName("describes exactly the route the controller serves")
    void routes() throws IOException {
        assertThat(contract().describedRoutes(), is(ModuleContract.servedRoutes(AdviceController.class, AccountAdviceController.class)));
    }

    @Test
    @DisplayName("each schema's properties are the record's fields")
    void schemas() throws IOException {
        assertThat(contract().properties("Signal"), is(ModuleContract.fields(Signal.class)));
        assertThat(contract().properties("SignalFigures"), is(ModuleContract.fields(SignalFigures.class)));
        assertThat(contract().properties("AccountAdvice"), is(ModuleContract.fields(AccountAdvice.class)));
        assertThat(contract().properties("AdviceItem"), is(ModuleContract.fields(AdviceItem.class)));
    }

    @Test
    @DisplayName("the directions are the ones the code knows")
    void directions() {
        assertThat(Arrays.stream(Direction.values()).map(Enum::name).collect(Collectors.toSet()),
                is(Set.of("BUY", "SELL", "HOLD")));
    }
}
