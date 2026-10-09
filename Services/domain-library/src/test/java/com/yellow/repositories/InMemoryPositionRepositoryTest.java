package com.yellow.repositories;

import com.yellow.entities.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class InMemoryPositionRepositoryTest {

    private InMemoryPositionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPositionRepository();
    }

    @Test
    @DisplayName("Should return empty when no position exists, or either id is null")
    void shouldReturnEmptyWhenNoPositionExistsOrEitherIdIsNull() {
        assertThat(repository.find(1L, 2L), is(equalTo(Optional.empty())));
        assertThat(repository.find(null, 2L), is(equalTo(Optional.empty())));
        assertThat(repository.find(1L, null), is(equalTo(Optional.empty())));
    }

    @Test
    @DisplayName("Should assign a position id when saving a new position")
    void shouldAssignPositionIdWhenSavingNewPosition() {
        Position saved = repository.save(new Position(null, 1L, 2L, new BigDecimal("10"), new BigDecimal("100.00")));
        assertThat(saved.positionId(), is(notNullValue()));
    }

    @Test
    @DisplayName("Should find a saved position by account and instrument")
    void shouldFindSavedPositionByAccountAndInstrument() {
        repository.save(new Position(null, 1L, 2L, new BigDecimal("10"), new BigDecimal("100.00")));
        assertThat(repository.find(1L, 2L).map(Position::quantity),
                is(equalTo(Optional.of(new BigDecimal("10.000000")))));
    }

    @Test
    @DisplayName("Should save several positions at once")
    void shouldSaveSeveralPositionsAtOnce() {
        repository.saveAll(
                new Position(null, 1L, 2L, new BigDecimal("10"), new BigDecimal("100.00")),
                new Position(null, 1L, 3L, new BigDecimal("5"), new BigDecimal("50.00")));

        assertThat(repository.count(), is(equalTo(2)));
    }

    @Test
    @DisplayName("Should list every position held by an account")
    void shouldListEveryPositionHeldByAnAccount() {
        repository.save(new Position(null, 1L, 2L, new BigDecimal("10"), new BigDecimal("100.00")));
        repository.save(new Position(null, 1L, 3L, new BigDecimal("5"), new BigDecimal("50.00")));

        assertThat(repository.findByAccount(1L), hasSize(2));
    }

    @Test
    @DisplayName("Should delete a position for a given account and instrument")
    void shouldDeletePositionForGivenAccountAndInstrument() {
        repository.save(new Position(null, 1L, 2L, new BigDecimal("10"), new BigDecimal("100.00")));

        repository.delete(1L, 2L);

        assertThat(repository.find(1L, 2L), is(equalTo(Optional.empty())));
    }

    @Test
    @DisplayName("Should clear all stored positions")
    void shouldClearAllStoredPositions() {
        repository.save(new Position(null, 1L, 2L, new BigDecimal("10"), new BigDecimal("100.00")));
        repository.clear();
        assertThat(repository.count(), is(equalTo(0)));
    }
}
