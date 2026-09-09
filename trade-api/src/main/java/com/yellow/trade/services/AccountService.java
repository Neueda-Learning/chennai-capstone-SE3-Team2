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
import com.yellow.trade.mappers.OrderHistoryRow;
import com.yellow.trade.mappers.OrderMapper;
import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.mappers.PositionRow;
import com.yellow.trade.security.TokenAccountContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class AccountService {

    private final AccountMapper accountMapper;
    private final PositionMapper positionMapper;
    private final OrderMapper orderMapper;
    private final TokenAccountContext tokenAccountContext;

    public AccountService(AccountMapper accountMapper, PositionMapper positionMapper,
                           OrderMapper orderMapper, TokenAccountContext tokenAccountContext) {
        this.accountMapper = accountMapper;
        this.positionMapper = positionMapper;
        this.orderMapper = orderMapper;
        this.tokenAccountContext = tokenAccountContext;
    }

    private AccountRow requireAccount(Long id) {
        AccountRow row = accountMapper.findById(id);
        if (row == null) {
            // reuse the domain's own exception -- @ControllerAdvice maps
            // this to ACC-404 wherever it's thrown, not just here
            throw new AccountNotFoundException(id);
        }
        // a token proves who you are, not what you may reach -- same ACC-403
        // message a suspended account gets, so wrong-token and suspended
        // look identical to the caller
        if (!id.equals(tokenAccountContext.currentAccountId())) {
            throw new AccountNotActiveException(row.status);
        }
        return row;
    }

    public AccountResponse getAccount(Long id) {
        AccountRow row = requireAccount(id);
        return new AccountResponse(row.id, row.accountId, row.holderName,
                row.cashBalance, row.status, row.version, row.lastUpdated);
    }

    public BalanceResponse getBalance(Long id) {
        AccountRow row = requireAccount(id);
        return new BalanceResponse(row.id, row.cashBalance, row.currency, Instant.now());
    }

    public List<PositionResponse> getPositions(Long id) {
        requireAccount(id); // ACC-404 must fire even if positions come back empty
        return positionMapper.findByAccountId(id).stream()
                // contract: zero-quantity holdings are not returned
                .filter(row -> row.quantity.signum() != 0)
                .map(row -> new PositionResponse(row.accountId, row.symbol,
                        row.quantity.intValueExact(), row.averageCost))
                .toList();
    }

    public List<OrderHistoryEntry> getOrders(Long id, OrderStatus status, Instant from, Instant to) {
        requireAccount(id);
        return orderMapper.findByAccountId(id, status, from, to).stream()
                .map(this::toOrderHistoryEntry)
                .toList();
    }

    private OrderHistoryEntry toOrderHistoryEntry(OrderHistoryRow row) {
        return new OrderHistoryEntry(
                "ORD-" + row.orderId, // contract: display prefix over the stored UUID
                row.accountId,
                row.symbol,
                row.side,
                row.quantity.intValueExact(),
                row.price,
                row.executedPrice,
                row.status,
                row.idempotencyKey,
                row.createdOn
        );
    }
}