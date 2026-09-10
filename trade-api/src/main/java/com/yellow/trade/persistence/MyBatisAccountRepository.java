package com.yellow.trade.persistence;

import com.yellow.entities.Account;
import com.yellow.repositories.AccountRepository;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Binds the domain's AccountRepository port to Postgres.
 *
 * The domain declares what it needs -- "give me the account with this key" --
 * and knows nothing about how. This class is the how, and it is the only
 * reason the same rules can run here against a database and in Sprint 5's
 * tests against a hash map.
 */
@Repository
public class MyBatisAccountRepository implements AccountRepository {

    private final AccountMapper accountMapper;

    public MyBatisAccountRepository(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Override
    public Optional<Account> findById(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        AccountRow row = accountMapper.findById(accountId);
        return Optional.ofNullable(row).map(RowMapping::toAccount);
    }
}
