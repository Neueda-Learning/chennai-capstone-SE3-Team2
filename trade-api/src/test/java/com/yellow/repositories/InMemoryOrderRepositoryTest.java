package com.yellow.repositories;

import com.yellow.entities.Order;
import com.yellow.enums.OrderSide;
import com.yellow.exceptions.DuplicateOrderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryOrderRepositoryTest {

    private InMemoryOrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderRepository();
    }

    private Order newOrder(String idempotencyKey) {
        return Order.place(1L, 2L, OrderSide.BUY, new BigDecimal("10"), new BigDecimal("100.00"), idempotencyKey);
    }

    @Test
    @DisplayName("Should assign an order id on first save")
    void shouldAssignOrderIdOnFirstSave() {
        Order saved = repository.save(newOrder("key-1234"));
        assertThat(saved.orderId(), is(notNullValue()));
    }

    @Test
    @DisplayName("Should report the key as used once the order carrying it is saved")
    void shouldReportKeyAsUsedOnceOrderCarryingItIsSaved() {
        repository.save(newOrder("key-1234"));
        assertThat(repository.existsByAccountAndKey(1L, "key-1234"), is(true));
    }

    @Test
    @DisplayName("Should report the key as unused for an unrelated account, key, or null")
    void shouldReportKeyAsUnusedForUnrelatedAccountKeyOrNull() {
        assertThat(repository.existsByAccountAndKey(1L, "never-used"), is(false));
        assertThat(repository.existsByAccountAndKey(null, "key-1234"), is(false));
    }

    @Test
    @DisplayName("Should refuse a second save carrying an already-used key for the same account")
    void shouldRefuseSecondSaveCarryingAlreadyUsedKeyForSameAccount() {
        repository.save(newOrder("key-1234"));
        assertThrows(DuplicateOrderException.class, () -> repository.save(newOrder("key-1234")));
    }

    @Test
    @DisplayName("Should find a saved order by id and by account-and-key")
    void shouldFindSavedOrderByIdAndByAccountAndKey() {
        Order saved = repository.save(newOrder("key-1234"));

        assertThat(repository.findById(saved.orderId()).isPresent(), is(true));
        assertThat(repository.findByAccountAndKey(1L, "key-1234").map(Order::orderId),
                is(equalTo(Optional.of(saved.orderId()))));
    }

    @Test
    @DisplayName("Should list every order placed by an account")
    void shouldListEveryOrderPlacedByAnAccount() {
        repository.save(newOrder("key-1"));
        repository.save(newOrder("key-2"));

        assertThat(repository.findByAccount(1L), hasSize(2));
        assertThat(repository.findAll(), hasSize(2));
    }

    @Test
    @DisplayName("Should clear all stored orders, and accept a fresh save afterward")
    void shouldClearAllStoredOrdersAndAcceptFreshSaveAfterward() {
        repository.save(newOrder("key-1234"));
        repository.clear();

        assertThat(repository.count(), is(equalTo(0)));
        Order freshOrder = repository.save(newOrder("key-1234"));
        assertThat(freshOrder.orderId(), is(notNullValue()));
    }
}
