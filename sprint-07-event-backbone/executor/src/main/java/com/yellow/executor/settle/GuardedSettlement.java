package com.yellow.executor.settle;

import com.yellow.enums.OrderStatus;
import com.yellow.executor.fill.FillDecision;
import com.yellow.executor.fill.OrderSnapshot;
import com.yellow.executor.persistence.ExecutionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Story 610's settlement: the guarded state transition, and nothing else.
 *
 * <p>This is deliberately the smaller half. It makes an order's outcome durable
 * and makes a replayed message harmless, which is what story 610 needs to
 * satisfy "an order that cannot be priced is resolved rather than left at NEW
 * for ever". It does NOT move cash and does NOT write a position: those are
 * story 611's, and they belong in the same transaction as this update, with
 * this update first.
 *
 * <p>STORY 611 REPLACES THIS CLASS, not {@link SettlementPort}. What has to
 * survive the replacement:
 *
 * <ul>
 *   <li>the conditional UPDATE is the FIRST write in the transaction, not a
 *       read followed by a decision followed by a write</li>
 *   <li>zero rows affected returns {@link SettlementResult#ALREADY_SETTLED}
 *       having changed nothing and published nothing</li>
 *   <li>the transaction encloses the three writes and nothing more -- no HTTP
 *       call, no Kafka publish inside it</li>
 * </ul>
 */
@Component
public class GuardedSettlement implements SettlementPort {

    private static final Logger log = LoggerFactory.getLogger(GuardedSettlement.class);

    private final ExecutionMapper mapper;
    private final Clock clock;

    public GuardedSettlement(ExecutionMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SettlementResult settle(FillDecision decision, OrderSnapshot order) {

        Instant now = Instant.now(clock);

        String status = decision.isFill()
                ? OrderStatus.FILLED.name()
                : OrderStatus.REJECTED.name();

        BigDecimal fillPrice = decision instanceof FillDecision.Fill fill
                ? fill.executedPrice()
                : null;

        // A filled order has no reason and a rejected one has no price. The
        // database enforces both, so a bug here surfaces as a constraint
        // violation rather than as a row that quietly disagrees with itself.
        String reason = decision instanceof FillDecision.Reject reject
                ? reject.reason().name()
                : null;

        int affected = mapper.settleIfNew(order.orderId(), status, fillPrice, now, reason);

        if (affected == 0) {
            // NOT an error. Another delivery of this same order got here first,
            // which is the ordinary consequence of at-least-once delivery.
            //
            // THIS LOG LINE IS THE DEMONSTRATION. Story 612 replays a message
            // off the topic and shows four things: the balance before, the
            // balance after, that no second event appeared on trade-events, and
            // this line. Do not make it quieter.
            log.info("duplicate delivery ignored: order {} on account {} was already settled, "
                            + "nothing written and nothing published",
                    order.orderId(), order.accountId());
            return SettlementResult.ALREADY_SETTLED;
        }

        if (decision instanceof FillDecision.Fill fill) {
            log.info("order {} FILLED: account={} {} {} of {} at {}",
                    order.orderId(), order.accountId(), order.side(),
                    order.quantity(), order.symbol(), fill.executedPrice());
        } else {
            log.info("order {} REJECTED: account={} {} {} of {}, reason={}",
                    order.orderId(), order.accountId(), order.side(),
                    order.quantity(), order.symbol(), reason);
        }

        return SettlementResult.SETTLED;
    }
}
