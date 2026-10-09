package com.yellow.trade.persistence;

import com.yellow.entities.Account;
import com.yellow.repositories.AccountRepository;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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
