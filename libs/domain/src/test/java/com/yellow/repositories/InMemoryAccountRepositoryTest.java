package com.yellow.repositories;

import com.yellow.entities.Account;
import com.yellow.enums.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class InMemoryAccountRepositoryTest {

    private InMemoryAccountRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAccountRepository();
    }

    private Account account(Long id, String reference) {
        return new Account(id, reference, 100L, new BigDecimal("500.00"), BigDecimal.ZERO, AccountStatus.ACTIVE, 1);
    }

    @Test
    @DisplayName("Should return empty when the account id is unknown or null")
    void shouldReturnEmptyWhenAccountIdIsUnknownOrNull() {
        assertThat(repository.findById(1L), is(equalTo(Optional.empty())));
        assertThat(repository.findById(null), is(equalTo(Optional.empty())));
    }

    @Test
    @DisplayName("Should find a saved account by id")
    void shouldFindSavedAccountById() {
        repository.save(account(1L, "REF-1"));
        assertThat(repository.findById(1L).map(Account::accountReference), is(equalTo(Optional.of("REF-1"))));
    }

    @Test
    @DisplayName("Should save several accounts at once")
    void shouldSaveSeveralAccountsAtOnce() {
        repository.saveAll(account(1L, "REF-1"), account(2L, "REF-2"));
        assertThat(repository.count(), is(equalTo(2)));
    }

    @Test
    @DisplayName("Should find an account by its reference")
    void shouldFindAccountByItsReference() {
        repository.save(account(1L, "REF-1"));
        assertThat(repository.findByReference("REF-1").map(Account::accountId), is(equalTo(Optional.of(1L))));
    }

    @Test
    @DisplayName("Should return empty when the reference is unknown or null")
    void shouldReturnEmptyWhenReferenceIsUnknownOrNull() {
        assertThat(repository.findByReference("NOPE"), is(equalTo(Optional.empty())));
        assertThat(repository.findByReference(null), is(equalTo(Optional.empty())));
    }

    @Test
    @DisplayName("Should clear all stored accounts")
    void shouldClearAllStoredAccounts() {
        repository.save(account(1L, "REF-1"));
        repository.clear();
        assertThat(repository.count(), is(equalTo(0)));
    }
}
