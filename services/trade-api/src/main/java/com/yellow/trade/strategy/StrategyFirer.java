package com.yellow.trade.strategy;

import com.yellow.trade.PlatformConstants;
import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.mappers.PositionRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * One strategy against one quote, in one transaction, with the strategy row
 * locked: whether it may fire (enabled and armed, read under the lock, so
 * disabling stops it at once), whether the quote crosses its level, whether
 * the order stays inside its bounds, and then the order, through the route.
 *
 * Bounded in code, at the point of decision: a buy may cost at most
 * maxSpend at its limit price, and leave at most maxPosition held; past
 * either it is refused, recorded, and STOPPED. A failed placement is counted;
 * the third stops it. Every outcome is a run, keyed on the quote, so a
 * replayed quote finds its run and places nothing again.
 */
@Service
public class StrategyFirer {

    private static final Logger log = LoggerFactory.getLogger(StrategyFirer.class);

    /** Room the limit leaves for the price to move before the fill: half a per cent, as the order window does. */
    static final BigDecimal PROTECTION = new BigDecimal("0.005");
    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    private final StrategyMapper strategies;
    private final PositionMapper positions;
    private final OrderPlacer placer;
    private final Clock clock;

    public StrategyFirer(StrategyMapper strategies, PositionMapper positions, OrderPlacer placer, Clock clock) {
        this.strategies = strategies;
        this.positions = positions;
        this.placer = placer;
        this.clock = clock;
    }

    @Transactional
    public void fire(long strategyId, StrategyQuote quote) {
        StrategyRow strategy = strategies.lockFireable(strategyId);
        if (strategy == null || !crosses(strategy, quote.price())) {
            return;
        }
        Instant now = clock.instant();
        boolean buy = "BUY".equals(strategy.getSide());
        BigDecimal limit = buy
                ? quote.ask().multiply(BigDecimal.ONE.add(PROTECTION)).setScale(2, RoundingMode.CEILING)
                : quote.bid().multiply(BigDecimal.ONE.subtract(PROTECTION)).setScale(2, RoundingMode.FLOOR);

        String refusal = buy ? refusal(strategy, limit) : null;
        if (refusal != null) {
            if (record(strategy, RunOutcome.REFUSED_LIMIT, quote, refusal, null, now)) {
                strategies.stop(strategyId);
                log.warn("strategy {} on account {} refused: {}", strategyId, strategy.getClientId(), refusal);
            }
            return;
        }
        // The run first, keyed on the quote: a replay stops here.
        RunRow placing = RunRow.of(strategyId, RunOutcome.PLACED, quote.price(), null, null, quote.eventId(), now);
        if (strategies.insertRun(placing) == 0) {
            log.info("strategy {} already ran on quote {}: nothing placed again", strategyId, quote.eventId());
            return;
        }
        OrderPlacer.Result result = placer.place(strategy.getClientId(), strategy.getSymbol(), strategy.getSide(),
                strategy.getQuantity(), limit, "strategy-" + strategyId + "-" + quote.eventId());
        if (result.placed()) {
            placing.setOrderId(result.orderId());
            strategies.markPlaced(placing.getRunId(), result.orderId());
            strategies.markFired(strategyId, now);
            log.info("strategy {} fired on account {}: order {} at limit {}", strategyId, strategy.getClientId(),
                    result.orderId(), limit);
            return;
        }
        placing.setOutcome(RunOutcome.FAILED.name());
        placing.setReason(result.reason());
        strategies.markFailed(placing.getRunId(), result.reason());
        int failures = strategies.recordFailure(strategyId);
        log.warn("strategy {} failed to place, failure {}: {}", strategyId, failures, result.reason());
        if (failures >= 3) {
            strategies.insertRun(RunRow.of(strategyId, RunOutcome.STOPPED, quote.price(),
                    "Stopped after three failures; switch it on again to re-arm it.", null, stopEvent(quote), now));
        }
    }

    private static boolean crosses(StrategyRow strategy, BigDecimal price) {
        int against = price.compareTo(strategy.getTriggerPrice());
        return "FALLS_THROUGH".equals(strategy.getTriggerKind()) ? against <= 0 : against >= 0;
    }

    /** Why a buy may not go ahead, or null. */
    private String refusal(StrategyRow strategy, BigDecimal limit) {
        BigDecimal cost = limit.multiply(BigDecimal.valueOf(strategy.getQuantity()));
        if (cost.compareTo(strategy.getMaxSpend()) > 0) {
            return "It would cost " + rupees(cost) + ", more than the " + rupees(strategy.getMaxSpend()) + " allowed a firing.";
        }
        PositionRow held = positions.findOne(strategy.getClientId(), strategy.getInstrumentId(),
                PlatformConstants.DEFAULT_POSITION_TYPE);
        BigDecimal after = (held == null ? BigDecimal.ZERO : held.getQuantity()).add(BigDecimal.valueOf(strategy.getQuantity()));
        if (after.compareTo(BigDecimal.valueOf(strategy.getMaxPosition())) > 0) {
            return "It would leave " + after.stripTrailingZeros().toPlainString() + " held, more than the "
                    + strategy.getMaxPosition() + " allowed.";
        }
        return null;
    }

    private boolean record(StrategyRow strategy, RunOutcome outcome, StrategyQuote quote, String reason,
                           java.util.UUID orderId, Instant now) {
        return strategies.insertRun(RunRow.of(strategy.getStrategyId(), outcome, quote.price(), reason, orderId,
                quote.eventId(), now)) > 0;
    }

    /** The STOPPED run's own key: the quote's, so a replay of it records nothing twice either. */
    private static java.util.UUID stopEvent(StrategyQuote quote) {
        return java.util.UUID.nameUUIDFromBytes(("stopped:" + quote.eventId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String rupees(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(INDIA).format(amount.setScale(2, RoundingMode.HALF_UP));
    }
}
