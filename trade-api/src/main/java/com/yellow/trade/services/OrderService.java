package com.yellow.trade.services;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.entities.Order;
import com.yellow.entities.Position;
import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.exceptions.OrderNotCancellableException;
import com.yellow.exceptions.OrderNotFoundException;
import com.yellow.exceptions.StaleAccountVersionException;
import com.yellow.trade.OrderIdentifier;
import com.yellow.trade.PlatformConstants;
import com.yellow.trade.dto.OrderResponse;
import com.yellow.trade.mappers.AccountMapper;
import com.yellow.trade.mappers.AccountRow;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.mappers.OrderMapper;
import com.yellow.trade.mappers.OrderRow;
import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.mappers.PositionRow;
import com.yellow.trade.security.CallerAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Order placement and cancellation.
 *
 * This class decides nothing about whether a trade is allowed. Rules 1 to 8 are
 * evaluated by the domain's own OrderService, against the same objects the
 * Sprint 7 executor will use. What this class owns is the three things the
 * domain deliberately does not: whether the caller may reach the account, the
 * transaction, and the lock.
 *
 * SYNCHRONOUS EXECUTION, and why.
 *
 * The contract is explicit that in Sprint 6 there is no Trade Executor, so
 * POST /api/v1/orders "validates, fills and persists inside one request". That
 * is what makes rules 9 and 10 real rather than aspirational: cash and position
 * move together, in one transaction, or neither moves.
 *
 * From Sprint 7 the same endpoint records the order NEW, publishes it to Kafka
 * and returns immediately, and the executor does the work below in another
 * process against a live quote. The contract permits both responses and tells
 * clients to handle an order that is still NEW when the response arrives. When
 * that happens, the fill moves out of this method and nothing else here changes.
 *
 * Because Sprint 6 has no live quote, an order fills at the price the customer
 * submitted. The executed price is still recorded separately from the limit
 * price, because from Sprint 7 the two genuinely differ.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** NUMERIC(18,4) on client_account.balance and orders.price. */
    private static final int MONEY_SCALE = 4;

    private final com.yellow.services.OrderService domainOrderService;
    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final OrderMapper orderMapper;
    private final PositionMapper positionMapper;
    private final CallerAccount caller;
    private final Clock clock;

    public OrderService(com.yellow.services.OrderService domainOrderService,
                        AccountMapper accountMapper,
                        InstrumentMapper instrumentMapper,
                        OrderMapper orderMapper,
                        PositionMapper positionMapper,
                        CallerAccount caller,
                        Clock clock) {
        this.domainOrderService = domainOrderService;
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.orderMapper = orderMapper;
        this.positionMapper = positionMapper;
        this.caller = caller;
        this.clock = clock;
    }

    /**
     * Places an order and, this sprint, fills it.
     *
     * The transaction encloses exactly the work that has to succeed or fail
     * together -- the order row, the cash and the holding -- and no more. The
     * reachability check above it needs no transaction, and the response is
     * built after it.
     *
     * An order recorded without its cash moved would let the same money be
     * spent twice; cash moved without a holding would lose the stock it bought.
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

        // Rules 1 to 8, in the domain, against MyBatis-backed repositories. The
        // order row is inserted by MyBatisOrderRepository.save() inside this
        // transaction. A refusal throws, and everything below is skipped.
        Order order = domainOrderService.placeOrder(request);

        BigDecimal fillPrice = order.limitPrice();
        BigDecimal consideration = money(order.quantity().multiply(fillPrice));

        moveCash(account, order.side(), consideration);
        movePosition(order, fillPrice);

        int filled = orderMapper.fillIfNew(order.orderId(), fillPrice, Instant.now(clock));
        if (filled == 0) {
            // Unreachable while placement fills in the transaction that
            // recorded the order. It stops being unreachable in Sprint 7.
            throw new OrderNotCancellableException(OrderStatus.NEW);
        }

        InstrumentRow instrument = instrumentMapper.findById(order.instrumentId());
        log.info("order {} filled: account={} {} {} of {} at {}",
                order.orderId(), accountId, order.side(), order.quantity(),
                instrument == null ? order.instrumentId() : instrument.getSymbol(), fillPrice);

        return new OrderResponse(
                OrderIdentifier.display(order.orderId()),
                OrderStatus.FILLED,
                messageFor(OrderStatus.FILLED),
                instrument == null ? null : instrument.getSymbol(),
                order.side(),
                order.quantity(),
                order.limitPrice());
    }

    /**
     * The optimistic lock, and the only place cash moves.
     *
     * The version the row was read at is named in the UPDATE and incremented by
     * it, so two writers racing on one account are serialised by the database:
     * the first affects one row, the second affects none. Zero rows affected is
     * NOT success -- it means somebody else wrote between our read and our
     * write, and the balance this order was judged against is stale.
     *
     * The answer is to refuse, not to retry. A retry would re-run rule 6
     * against the new balance and might succeed, which would mean a customer's
     * single click spent money they saw a different figure for.
     */
    private void moveCash(AccountRow account, OrderSide side, BigDecimal consideration) {
        int affected = side == OrderSide.BUY
                ? accountMapper.debitBalance(account.getClientId(), consideration, account.getVersion())
                : accountMapper.creditBalance(account.getClientId(), consideration, account.getVersion());

        if (affected == 0) {
            log.warn("ORD-409: optimistic lock lost on account {} at version {}",
                    account.getClientId(), account.getVersion());
            throw new StaleAccountVersionException(account.getClientId(), account.getVersion());
        }
    }

    /**
     * Moves the holding, using the domain's own average-cost arithmetic rather
     * than repeating it in SQL.
     *
     * The asymmetry belongs to the domain and is worth not losing: a buy
     * recalculates the average across the old holding and the new units; a sell
     * reduces the quantity and leaves the average alone, which is what makes
     * realised profit and loss computable at the point of sale.
     */
    private void movePosition(Order order, BigDecimal fillPrice) {
        Long accountId = order.accountId();
        Long instrumentId = order.instrumentId();
        String type = PlatformConstants.DEFAULT_POSITION_TYPE;

        PositionRow existing = positionMapper.findOne(accountId, instrumentId, type);

        if (order.isBuy()) {
            if (existing == null) {
                Position opened = Position.opening(accountId, instrumentId, order.quantity(), fillPrice);
                positionMapper.insertPosition(accountId, instrumentId, type,
                        opened.quantity(), opened.averagePrice());
                return;
            }
            Position held = held(existing, accountId, instrumentId);
            held.applyBuy(order.quantity(), fillPrice);
            positionMapper.updatePosition(accountId, instrumentId, type,
                    held.quantity(), held.averagePrice());
            return;
        }

        // Rule 7 already established the holding is there and large enough, so
        // a missing row here would be a defect rather than a customer error.
        Position held = held(existing, accountId, instrumentId);
        held.applySell(order.quantity());

        if (held.isClosed()) {
            // Deleted rather than zeroed: ck_position_quantity_positive says a
            // row is a real holding, and how it got there lives in the orders.
            positionMapper.deletePosition(accountId, instrumentId, type);
        } else {
            positionMapper.updatePosition(accountId, instrumentId, type,
                    held.quantity(), held.averagePrice());
        }
    }

    private static Position held(PositionRow row, Long accountId, Long instrumentId) {
        return new Position(row.getPositionId(), accountId, instrumentId,
                row.getQuantity(), row.getAveragePrice());
    }

    /**
     * Cancels an order.
     *
     * The transition is one conditional statement rather than a read followed
     * by a write: reading the status, deciding, and then writing would let the
     * executor fill the order in between, and the cancel would overwrite the
     * fill. Naming NEW in the WHERE clause makes the database arbitrate.
     *
     * Nothing is cancellable while Sprint 6 fills synchronously -- every order
     * is terminal by the time the response leaves. The path exists because the
     * contract fixes it, and Sprint 7 makes it reachable.
     */
    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        OrderRow existing = orderMapper.findById(orderId);

        if (existing == null) {
            // The contract pairs a 404 status with the ORD-409 code here,
            // because the catalogue has no ORD-404. The handler owns the
            // pairing; this only says which case it is.
            log.warn("cancel requested for unknown order {}", orderId);
            throw new OrderNotFoundException(orderId);
        }
        if (!caller.canReach(existing.getClientId())) {
            log.warn("ACC-403: token for account {} tried to cancel order {} on account {}",
                    caller.accountId(), orderId, existing.getClientId());
            throw new AccountNotActiveException(null);
        }

        int affected = orderMapper.cancelIfNew(orderId, Instant.now(clock));
        if (affected == 0) {
            log.warn("ORD-409: order {} was {} when cancel ran", orderId, existing.getStatus());
            throw new OrderNotCancellableException(existing.getStatus());
        }

        InstrumentRow instrument = instrumentMapper.findById(existing.getInstrumentId());
        log.info("order {} cancelled on account {}", orderId, existing.getClientId());

        return new OrderResponse(
                OrderIdentifier.display(existing.getOrderId()),
                OrderStatus.CANCELLED,
                messageFor(OrderStatus.CANCELLED),
                instrument == null ? null : instrument.getSymbol(),
                existing.getSide(),
                existing.getQuantity(),
                existing.getPrice());
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
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
