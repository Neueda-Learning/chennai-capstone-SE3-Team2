package com.yellow.repositories;

import com.yellow.entities.Account;

import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findById(Long accountId);
}
