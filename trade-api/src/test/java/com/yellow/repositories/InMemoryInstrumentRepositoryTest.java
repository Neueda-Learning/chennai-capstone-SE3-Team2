package com.yellow.repositories;

import com.yellow.entities.Instrument;
import com.yellow.enums.AssetClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryInstrumentRepositoryTest {

    private InMemoryInstrumentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryInstrumentRepository();
    }

    private Instrument instrument(Long id, String symbol) {
        return new Instrument(id, symbol, "Apple Inc", AssetClass.EQUITY, "USD", true);
    }

    @Test
    @DisplayName("Should return empty when the symbol is unknown, blank or null")
    void shouldReturnEmptyWhenSymbolIsUnknownBlankOrNull() {
        assertThat(repository.findBySymbol("AAPL"), is(equalTo(Optional.empty())));
        assertThat(repository.findBySymbol(""), is(equalTo(Optional.empty())));
        assertThat(repository.findBySymbol(null), is(equalTo(Optional.empty())));
    }

    @Test
    @DisplayName("Should find a saved instrument by symbol and by id")
    void shouldFindSavedInstrumentBySymbolAndById() {
        repository.save(instrument(1L, "AAPL"));

        assertThat(repository.findBySymbol("AAPL").map(Instrument::instrumentId), is(equalTo(Optional.of(1L))));
        assertThat(repository.findById(1L).map(Instrument::symbol), is(equalTo(Optional.of("AAPL"))));
    }

    @Test
    @DisplayName("Should return empty when the instrument id is unknown or null")
    void shouldReturnEmptyWhenInstrumentIdIsUnknownOrNull() {
        assertThat(repository.findById(99L), is(equalTo(Optional.empty())));
        assertThat(repository.findById(null), is(equalTo(Optional.empty())));
    }

    @Test
    @DisplayName("Should refuse to save a different instrument under a symbol already held by another id")
    void shouldRefuseToSaveDifferentInstrumentUnderSymbolAlreadyHeldByAnotherId() {
        repository.save(instrument(1L, "AAPL"));
        assertThrows(IllegalStateException.class, () -> repository.save(instrument(2L, "AAPL")));
    }

    @Test
    @DisplayName("Should save several instruments at once")
    void shouldSaveSeveralInstrumentsAtOnce() {
        repository.saveAll(instrument(1L, "AAPL"), instrument(2L, "MSFT"));
        assertThat(repository.count(), is(equalTo(2)));
    }

    @Test
    @DisplayName("Should clear all stored instruments")
    void shouldClearAllStoredInstruments() {
        repository.save(instrument(1L, "AAPL"));
        repository.clear();
        assertThat(repository.count(), is(equalTo(0)));
    }
}
