package com.yellow.repositories;

import com.yellow.entities.Account;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAccountRepository implements AccountRepository {

    private final Map<Long, Account> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<Account> findById(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(accountId));
    }

    public Account save(Account account) {
        byId.put(account.accountId(), account);
        return account;
    }

    public void saveAll(Account... accounts) {
        for (Account account : accounts) {
            save(account);
        }
    }

    public Optional<Account> findByReference(String accountReference) {
        if (accountReference == null) {
            return Optional.empty();
        }
        return byId.values().stream()
                .filter(a -> accountReference.equals(a.accountReference()))
                .findFirst();
    }

    public int count() {
        return byId.size();
    }

    public void clear() {
        byId.clear();
    }
}