package com.yellow.entities;

import com.yellow.enums.AssetClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class InstrumentTest {

    @Test
    @DisplayName("Should be tradable when constructed as tradable")
    void shouldBeTradableWhenConstructedAsTradable() {
        Instrument instrument = new Instrument(1L, "AAPL", "Apple Inc", AssetClass.EQUITY, "USD", true);
        assertThat(instrument.isTradable(), is(true));
    }

    @Test
    @DisplayName("Should become non-tradable after being delisted without losing its identity")
    void shouldBecomeNonTradableAfterBeingDelistedWithoutLosingIdentity() {
        Instrument instrument = new Instrument(1L, "AAPL", "Apple Inc", AssetClass.EQUITY, "USD", true);

        instrument.delist();

        assertThat(instrument.isTradable(), is(false));
        assertThat("delisting is a flag, not a deleted row thus identity must survive",
                instrument.instrumentId(), is(1L));
    }

    @Test
    @DisplayName("Should become tradable again after being relisted")
    void shouldBecomeTradableAgainAfterBeingRelisted() {
        Instrument instrument = new Instrument(1L, "AAPL", "Apple Inc", AssetClass.EQUITY, "USD", false);

        instrument.relist();

        assertThat(instrument.isTradable(), is(true));
    }
}