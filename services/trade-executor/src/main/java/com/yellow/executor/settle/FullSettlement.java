package com.yellow.executor.settle;

import com.yellow.executor.fill.ExecutionPrice;
import com.yellow.executor.fill.FillDecision;
import com.yellow.executor.fill.OrderSnapshot;
import com.yellow.executor.persistence.AccountRow;
import com.yellow.executor.persistence.ExecutionMapper;
import com.yellow.executor.persistence.PositionRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

/**
 * Story 611: settle the order, move the cash, and write the position in one
 * atomic transaction. Three writes, always in this order: UPDATE orders WHERE
 * status = 'NEW' -- the duplicate guard.
 */
@Component
public class FullSettlement implements SettlementPort {

    private static final Logger log = LoggerFactory.getLogger(FullSettlement.class);

    /** Package-private so the unit test can assert exactly this many retries. */
    static final int MAX_LOCK_ATTEMPTS = 5;

    private static final String POSITION_TYPE = "DELIVERY";

    private final ExecutionMapper mapper;
    private final Clock clock;

    public FullSettlement(ExecutionMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SettlementResult settle(FillDecision decision, OrderSnapshot order) {

        Instant now = Instant.now(clock);
        boolean isFill = decision instanceof FillDecision.Fill;
        String status = isFill ? "FILLED" : "REJECTED";
        BigDecimal fillPrice = isFill
                ? ((FillDecision.Fill) decision).executedPrice() : null;
        String reason = !isFill
                ? ((FillDecision.Reject) decision).reason().name() : null;

        int affected = mapper.settleIfNew(order.orderId(), status, fillPrice, now, reason);

        if (affected == 0) {
            log.info("duplicate delivery ignored: order {} on account {} was already settled, "
                            + "nothing written and nothing published",
                    order.orderId(), order.accountId());
            return SettlementResult.ALREADY_SETTLED;
        }

        // What the trade costs/yields, rounded to the column's precision.
        BigDecimal blockedRelease = ExecutionPrice.round(
                order.limitPrice().multiply(order.quantity()));

        BigDecimal balanceDelta;
        BigDecimal blockedDelta;

        if (isFill) {
            BigDecimal consideration = order.considerationAt(
                    ((FillDecision.Fill) decision).executedPrice());
            if (order.isBuy()) {
                // BUY FILL: debit the actual cost, release what was blocked.
                balanceDelta = consideration.negate();
                blockedDelta = blockedRelease.negate();
            } else {
                // SELL FILL: credit the proceeds; no blocked funds on sells.
                balanceDelta = consideration;
                blockedDelta = BigDecimal.ZERO;
            }
        } else {
            // REJECT: no cash flow; BUY releases its blocked funds, SELL has none.
            balanceDelta = BigDecimal.ZERO;
            blockedDelta = order.isBuy() ? blockedRelease.negate() : BigDecimal.ZERO;
        }

        if (balanceDelta.compareTo(BigDecimal.ZERO) != 0
                || blockedDelta.compareTo(BigDecimal.ZERO) != 0) {
            updateAccountWithRetry(order, balanceDelta, blockedDelta);
        }

        if (isFill) {
            writePosition(order, ((FillDecision.Fill) decision).executedPrice());
        }

        if (isFill) {
            log.info("order {} FILLED: account={} {} {} of {} at {}",
                    order.orderId(), order.accountId(), order.side(),
                    order.quantity(), order.symbol(), fillPrice);
        } else {
            log.info("order {} REJECTED: account={} {} {} of {}, reason={}",
                    order.orderId(), order.accountId(), order.side(),
                    order.quantity(), order.symbol(), reason);
        }

        return SettlementResult.SETTLED;
    }

    private void updateAccountWithRetry(OrderSnapshot order,
                                        BigDecimal balanceDelta,
                                        BigDecimal blockedDelta) {
        AccountRow account = mapper.findAccount(order.accountId());
        for (int attempt = 1; attempt <= MAX_LOCK_ATTEMPTS; attempt++) {
            int rows = mapper.updateAccount(
                    order.accountId(), balanceDelta, blockedDelta, account.getVersion());
            if (rows == 1) {
                return;
            }
            log.warn("optimistic lock lost on account {} (attempt {}/{})",
                    order.accountId(), attempt, MAX_LOCK_ATTEMPTS);
            if (attempt < MAX_LOCK_ATTEMPTS) {
                account = mapper.findAccount(order.accountId());
            }
        }
        throw new LockExhaustedException(
                "could not acquire optimistic lock on account " + order.accountId()
                        + " after " + MAX_LOCK_ATTEMPTS + " attempts; "
                        + "transaction rolled back, order stays NEW");
    }

    private void writePosition(OrderSnapshot order, BigDecimal executedPrice) {
        PositionRow existing = mapper.findPosition(
                order.accountId(), order.instrumentId(), POSITION_TYPE);

        if (order.isBuy()) {
            if (existing == null) {
                mapper.insertPosition(order.accountId(), order.instrumentId(),
                        POSITION_TYPE, order.quantity(), executedPrice);
            } else {
                BigDecimal newQty = existing.getQuantity().add(order.quantity());
                BigDecimal newAvg = existing.getQuantity()
                        .multiply(existing.getAveragePrice())
                        .add(order.quantity().multiply(executedPrice))
                        .divide(newQty, ExecutionPrice.SCALE, RoundingMode.HALF_UP);
                mapper.updatePosition(order.accountId(), order.instrumentId(),
                        POSITION_TYPE, newQty, newAvg);
            }
        } else {
            if (existing == null) {
                // atExecution should have rejected this order. Logging the
                // anomaly here is defensive; the fill already wrote to
                log.warn("SELL fill for order {} has no open position: "
                                + "account={} instrument={} -- atExecution should have caught this",
                        order.orderId(), order.accountId(), order.instrumentId());
                return;
            }
            BigDecimal newQty = existing.getQuantity().subtract(order.quantity());
            if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                mapper.deletePosition(order.accountId(), order.instrumentId(), POSITION_TYPE);
            } else {
                mapper.updatePosition(order.accountId(), order.instrumentId(),
                        POSITION_TYPE, newQty, existing.getAveragePrice());
            }
        }
    }
}
