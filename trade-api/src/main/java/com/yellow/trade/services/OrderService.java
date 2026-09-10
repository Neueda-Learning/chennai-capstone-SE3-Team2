package com.yellow.trade.services;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.entities.Order;
import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.exceptions.StaleAccountVersionException;
import com.yellow.exceptions.OrderNotCancellableException;
import com.yellow.trade.dto.OrderResponse;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.mappers.OrderMapper;
import com.yellow.trade.mappers.OrderRow;
import com.yellow.trade.security.CallerAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Order placement and cancellation.
 *
 * This class decides nothing about whether a trade is allowed. Rules 1 to 8
 * are evaluated by the domain's own OrderService, against the same objects the
 * Sprint 7 executor will use, and this class does three things around them:
 * it answers whether the caller may reach the account, it holds the
 * transaction, and it takes the lock.
 *
 * WHAT PLACEMENT MOVES, and why it is not a debit.
 *
 * A placed order is NEW. It has not filled -- filling is the Trade Executor's
 * job against a live quote in Sprint 7, and until then nobody knows what it
 * costs. So placement does not take the cash; it BLOCKS it. balance is
 * unchanged, blocked_funds rises, and available funds -- balance minus blocked
 * -- falls, which is the figure rule 6 tests the next buy against.
 *
 * That is what the schema was built for: blocked_funds is documented as "the
 * subset of balance held against pending orders". The concurrency behaviour
 * the brief asks about is identical either way. Two buys of 20,000 against a
 * balance of 25,000: the first blocks 20,000 and leaves 5,000 available, and
 * the second is refused -- by rule 6 if it read the row afterwards, and by the
 * version check below if it read the row at the same moment. Neither one
 * silently overwrites the other.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** NUMERIC(18,4) on client_account.balance and blocked_funds. */
    private static final int MONEY_SCALE = 4;

    private final com.yellow.services.OrderService domainOrderService;
    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final OrderMapper orderMapper;
    private final CallerAccount caller;
    private final Clock clock;

    public OrderService(com.yellow.services.OrderService domainOrderService,
                        AccountMapper accountMapper,
                        InstrumentMapper instrumentMapper,
                        OrderMapper orderMapper,
                        CallerAccount caller,
                        Clock clock) {
        this.domainOrderService = domainOrderService;
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.orderMapper = orderMapper;
        this.caller = caller;
        this.clock = clock;
    }

    /**
     * Places an order.
     *
     * The transaction encloses exactly the work that has to succeed or fail
     * together -- the order row and the cash it commits -- and no more. The
     * reachability check above it needs no transaction, and the response is
     * built after it.
     *
     * Everything inside rolls back together. An order recorded without its
     * cash blocked would let the same money be spent twice; cash blocked
     * without an order recorded would strand it with nothing to release it.
     */
    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        Long accountId = request.getAccountId();

        // Answered here, where the account key is known, rather than in the
        // token filter. The account must exist before the caller can be told
        // anything about it, so rule 1's answer comes first.
        AccountRow account = accountMapper.findById(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        if (!caller.canReach(accountId)) {
            log.warn("ACC-403: token for account {} tried to place an order on account {}",
                    caller.accountId(), accountId);
            throw new AccountNotActiveException(account.getStatus());
        }

        // Rules 1 to 8, in the domain, against MyBatis-backed repositories.
        // The order row is inserted by MyBatisOrderRepository.save() inside
        // this transaction. A refusal throws, and everything below is skipped.
        Order order = domainOrderService.placeOrder(request);

        // Rule 9's other half: the cash that this order commits.
        if (order.side() == OrderSide.BUY) {
            blockFunds(account, order);
        }
        // A SELL commits no cash. It commits stock, and the schema has no
        // blocked-quantity column -- rule 7 checked the holding is there, and
        // the holding moves when the order fills in Sprint 7.

        InstrumentRow instrument = instrumentMapper.findById(order.instrumentId());
        log.info("order {} placed: account={} {} {} of {} at {}",
                order.orderId(), accountId, order.side(), order.quantity(),
                instrument == null ? order.instrumentId() : instrument.getSymbol(), order.limitPrice());

        return toResponse(order, instrument);
    }

    /**
     * The optimistic lock, and the only place cash is committed.
     *
     * The version the row was read at is named in the UPDATE and incremented
     * by it, so two writers racing on one account are serialised by the
     * database: the first affects one row, the second affects none. Zero rows
     * affected is NOT success -- it means somebody else wrote between our read
     * and our write, and the balance we based this order on is stale.
     *
     * The answer is to refuse, not to retry. A retry would re-run rule 6
     * against the new balance and might succeed, which is defensible -- but it
     * would also mean a customer's single click can spend money they saw a
     * different figure for. ORD-409 tells the client to read the balance again
     * and decide.
     */
    private void blockFunds(AccountRow account, Order order) {
        BigDecimal notional = order.notionalValue();

        int affected = accountMapper.blockFunds(
                account.getClientId(), notional, account.getVersion());

        if (affected == 0) {
            log.warn("ORD-409: optimistic lock lost on account {} at version {} for order {}",
                    account.getClientId(), account.getVersion(), order.orderId());
            // Rolls back the order row inserted moments ago, which is the
            // point of the transaction: the two move together or neither does.
            throw new StaleAccountVersionException(account.getClientId(), account.getVersion());
        }
    }

    /**
     * Cancels an order.
     *
     * The transition is one conditional statement rather than a read followed
     * by a write: reading the status, deciding, and then writing would let the
     * executor fill the order in between, and the cancel would overwrite the
     * fill. Naming NEW in the WHERE clause makes the database arbitrate.
     */
    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        OrderRow existing = orderMapper.findById(orderId);

        // An unknown id and an already-terminal order answer identically. The
        // catalogue has no ORD-404, and a distinct answer would let a caller
        // with a valid token discover which order ids exist.
        if (existing == null) {
            log.warn("ORD-409: cancel requested for unknown order {}", orderId);
            throw new OrderNotCancellableException(null);
        }
        if (!caller.canReach(existing.getClientId())) {
            log.warn("ACC-403: token for account {} tried to cancel order {} on account {}",
                    caller.accountId(), orderId, existing.getClientId());
            throw new AccountNotActiveException(null);
        }

        Instant now = Instant.now(clock);
        int affected = orderMapper.cancelIfNew(orderId, now);
        if (affected == 0) {
            log.warn("ORD-409: order {} was {} when cancel ran", orderId, existing.getStatus());
            throw new OrderNotCancellableException(existing.getStatus());
        }

        // Releasing the blocked cash is part of the same transaction, under
        // the same lock. A cancel that freed the order but not the money would
        // leave the customer unable to spend funds no order is holding.
        if (existing.getSide() == OrderSide.BUY) {
            releaseFunds(existing);
        }

        InstrumentRow instrument = instrumentMapper.findById(existing.getInstrumentId());
        log.info("order {} cancelled on account {}", orderId, existing.getClientId());

        return new OrderResponse(
                existing.getOrderId(),
                existing.getClientId(),
                instrument == null ? null : instrument.getSymbol(),
                existing.getSide(),
                existing.getQuantity(),
                existing.getPrice(),
                OrderStatus.CANCELLED,
                messageFor(OrderStatus.CANCELLED),
                existing.getDatePlaced());
    }

    private void releaseFunds(OrderRow order) {
        if (order.getPrice() == null) {
            // A MARKET order with no limit price blocked no cash when it was
            // placed -- it did not come through this service, which requires
            // a price on every request. Releasing an amount we never blocked
            // would credit the customer funds that were never held.
            return;
        }
        AccountRow account = accountMapper.findById(order.getClientId());
        // Scaled to the column's four places rather than left at the ten the
        // raw product carries: the amount released has to be the amount that
        // was blocked, to the digit, or blocked_funds drifts a fraction of a
        // paisa every cancel and stops reconciling.
        BigDecimal notional = order.getPrice()
                .multiply(order.getQuantity())
                .setScale(MONEY_SCALE, java.math.RoundingMode.HALF_UP);

        int affected = accountMapper.releaseFunds(
                account.getClientId(), notional, account.getVersion());

        if (affected == 0) {
            log.warn("ORD-409: could not release {} on account {} at version {}",
                    notional, account.getClientId(), account.getVersion());
            throw new OrderNotCancellableException(order.getStatus());
        }
    }

    private OrderResponse toResponse(Order order, InstrumentRow instrument) {
        return new OrderResponse(
                order.orderId(),
                order.accountId(),
                instrument == null ? null : instrument.getSymbol(),
                order.side(),
                order.quantity(),
                order.limitPrice(),
                order.status(),
                messageFor(order.status()),
                order.placedAt());
    }

    /** Display only, per the contract. Never branch on this string. */
    private static String messageFor(OrderStatus status) {
        return switch (status) {
            case NEW -> "Order accepted";
            case FILLED -> "Order executed";
            case REJECTED -> "Order rejected";
            case CANCELLED -> "Order cancelled";
        };
    }
}
