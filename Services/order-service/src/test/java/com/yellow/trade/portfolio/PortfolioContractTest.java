package com.yellow.trade.portfolio;

import com.yellow.trade.marketdata.PricingUnavailableException;
import com.yellow.trade.modules.ModuleContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/** Contracts/API Schemas/portfolio-api.yaml binds: the routes it fixes and the fields on every response. */
class PortfolioContractTest {

    private static ModuleContract contract() throws IOException {
        return ModuleContract.of("../../Contracts/API Schemas/portfolio-api.yaml");
    }

    @Test
    @DisplayName("the three routes and the health check, exactly")
    void routes() throws IOException {
        assertThat(contract().describedRoutes(),
                is(ModuleContract.servedRoutes(PortfolioController.class, PortfolioHealthController.class)));
    }

    @Test
    @DisplayName("every response's fields, by the contract's names")
    void schemas() throws IOException {
        assertThat(contract().properties("PortfolioSummary"), is(ModuleContract.fields(PortfolioSummary.class)));
        assertThat(contract().properties("PricedPosition"), is(ModuleContract.fields(PricedPosition.class)));
        assertThat(contract().properties("PnlResponse"), is(ModuleContract.fields(PnlResponse.class)));
        assertThat(contract().properties("SymbolPnl"), is(ModuleContract.fields(SymbolPnl.class)));
        assertThat(contract().properties("HealthResponse"), is(ModuleContract.fields(HealthResponse.class)));
        assertThat(ModuleContract.fields(HealthResponse.Dependency.class), is(Set.of("name", "status", "quotaRemaining")));
    }

    @Test
    @DisplayName("MKT-503 is in the contract's catalogue")
    void pricingCode() throws IOException {
        assertThat(contract().errorCodes(), hasItem(PricingUnavailableException.CODE));
    }
}
