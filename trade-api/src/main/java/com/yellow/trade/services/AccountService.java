package com.yellow.trade.services;

import com.yellow.enums.OrderStatus;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.trade.dto.AccountResponse;
import com.yellow.trade.dto.BalanceResponse;
import com.yellow.trade.dto.OrderHistoryEntry;
import com.yellow.trade.dto.PositionResponse;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import com.yellow.trade.mappers.OrderMapper;
import com.yellow.trade.mappers.OrderRow;
import com.yellow.trade.PlatformConstants;
import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.security.CallerAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The four read operations.
 *
 * Every one of them starts by resolving the account, which answers ACC-404 and
 * ACC-403 in one place. That matters more than it looks: an endpoint that
 * skipped the check because it "only returns an empty list anyway" would tell
 * a caller with a valid token whether an account key exists.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountMapper accountMapper;
    private final PositionMapper positionMapper;
    private final OrderMapper orderMapper;
    private final CallerAccount caller;
    private final Clock clock;

    public AccountService(AccountMapper accountMapper,
                          PositionMapper positionMapper,
                          OrderMapper orderMapper,
                          CallerAccount caller,
                          Clock clock) {
        this.accountMapper = accountMapper;
        this.positionMapper = positionMapper;
        this.orderMapper = orderMapper;
        this.caller = caller;
        this.clock = clock;
    }

    /**
     * Resolves an account the caller is allowed to see, or refuses.
     *
     * Note what is NOT checked here: whether the account is ACTIVE. A
     * SUSPENDED account can be read and cannot trade -- suspension is
     * reversible and the holder still needs to see their own money. Trading
     * is where status is enforced, by rule 2, in the domain.
     */
    private AccountRow requireReachableAccount(Long accountId) {
        AccountRow row = accountMapper.findById(accountId);
        if (row == null) {
            throw new AccountNotFoundException(accountId);
        }
        if (!caller.canReach(accountId)) {
            // The same code and the same message a suspended account gets.
            // A caller holding a valid token for account 4 must not be able to
            // tell "account 7 exists but is not yours" from "account 7 is
            // suspended" -- either answer, repeated across a range of keys,
            // enumerates the account table.
            log.warn("ACC-403: token for account {} addressed account {}",
                    caller.accountId(), accountId);
            throw new AccountNotActiveException(row.getStatus());
        }
        return row;
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        AccountRow row = requireReachableAccount(accountId);
        return new AccountResponse(
                row.getAccountRef(),      // the string business reference
                row.getHolderName(),
                row.getStatus(),
                row.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accountId) {
        AccountRow row = requireReachableAccount(accountId);
        return new BalanceResponse(
                row.getClientId(),
                row.getBalance(),
                row.getBlockedFunds(),
                row.availableFunds(),
                PlatformConstants.QUOTE_CURRENCY,
                Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(Long accountId) {
        requireReachableAccount(accountId);
        return positionMapper.findByAccountId(accountId).stream()
                .map(row -> new PositionResponse(
                        row.getClientId(),
                        row.getSymbol(),
                        row.getPositionType(),
                        row.getQuantity(),
                        row.getAveragePrice()))
                .toList();
    }

    /**
     * The status filter is bound as a parameter like every other value. It is
     * converted to its name here rather than passed as an enum so the mapper
     * binds a plain string -- and it can only ever be one of four literals,
     * because Spring rejected anything else before this method was entered.
     */
    @Transactional(readOnly = true)
    public List<OrderHistoryEntry> getOrders(Long accountId, OrderStatus status, Instant from, Instant to) {
        requireReachableAccount(accountId);
        return orderMapper.findByAccountId(accountId, status == null ? null : status.name(), from, to).stream()
                .map(AccountService::toHistoryEntry)
                .toList();
    }

    private static OrderHistoryEntry toHistoryEntry(OrderRow row) {
        return new OrderHistoryEntry(
                row.getOrderId(),
                row.getClientId(),
                row.getSymbol(),
                row.getSide(),
                row.getQuantity(),
                row.getPrice(),
                row.getFillPrice(),
                row.getStatus(),
                row.getIdempotencyKey(),
                row.getDatePlaced(),
                row.getResolvedAt());
    }
}
