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
 * Story 610's settlement: the guarded state transition, and nothing else. This
 * is deliberately the smaller half.
 */
// @Component removed: FullSettlement (story 611) is the active implementation.
// This class is kept as reference for the story 610 guard logic.
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

        // A filled order has no reason and a rejected one has no price.
        String reason = decision instanceof FillDecision.Reject reject
                ? reject.reason().name()
                : null;

        int affected = mapper.settleIfNew(order.orderId(), status, fillPrice, now, reason);

        if (affected == 0) {
            // NOT an error. Another delivery of this same order got here
            // first, which is the ordinary consequence of at-least-once
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
