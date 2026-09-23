package com.yellow.executor.consume;

import com.yellow.entities.Account;
import com.yellow.entities.Instrument;
import com.yellow.entities.Position;
import com.yellow.enums.AssetClass;
import com.yellow.executor.checks.PreTradeChecks;
import com.yellow.executor.events.EventEnvelope;
import com.yellow.executor.events.TradeEventPayload;
import com.yellow.executor.fill.EquityFillRule;
import com.yellow.executor.fill.FillDecision;
import com.yellow.executor.fill.FillRule;
import com.yellow.executor.fill.MutualFundFillRule;
import com.yellow.executor.fill.OrderSnapshot;
import com.yellow.executor.fill.RejectReason;
import com.yellow.executor.persistence.AccountRow;
import com.yellow.executor.persistence.ExecutableOrderRow;
import com.yellow.executor.persistence.ExecutionMapper;
import com.yellow.executor.persistence.PositionRow;
import com.yellow.executor.persistence.RowMapping;
import com.yellow.executor.quotes.Quote;
import com.yellow.executor.quotes.QuoteSource;
import com.yellow.executor.quotes.QuoteUnavailableException;
import com.yellow.executor.settle.SettlementPort;
import com.yellow.executor.settle.SettlementResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** What the executor does with one order. */
@Service
public class OrderExecutionService {

    private static final Logger log = LoggerFactory.getLogger(OrderExecutionService.class);

    /**
     * Sprint 3's default. Every position this platform creates is DELIVERY:
     * intraday positions are squared off by an evening job that does not
     * exist.
     */
    private static final String POSITION_TYPE = "DELIVERY";

    private final ExecutionMapper mapper;
    private final QuoteSource quotes;
    private final SettlementPort settlement;
    private final KafkaTemplate<String, EventEnvelope<TradeEventPayload>> tradeEventTemplate;
    private final Clock clock;

    @Value("${executor.topics.trade-events:trade-events}")
    private String tradeEventsTopic;

    private final FillRule equityRule = new EquityFillRule();
    private final FillRule mutualFundRule = new MutualFundFillRule();

    public OrderExecutionService(ExecutionMapper mapper,
                                 QuoteSource quotes,
                                 SettlementPort settlement,
                                 KafkaTemplate<String, EventEnvelope<TradeEventPayload>> tradeEventTemplate,
                                 Clock clock) {
        this.mapper = mapper;
        this.quotes = quotes;
        this.settlement = settlement;
        this.tradeEventTemplate = tradeEventTemplate;
        this.clock = clock;
    }

    public Optional<SettlementResult> execute(UUID orderId) {

        ExecutableOrderRow row = mapper.findOrder(orderId);
        if (row == null) {
            // Not a transient failure: no amount of retrying puts a row in the
            // table. Story 613 dead-letters this on the first attempt.
            throw new UnknownOrderException(orderId);
        }

        OrderSnapshot order = RowMapping.toSnapshot(row);

        if (!order.isWorking()) {
            // Cheap early exit.
            log.info("order {} is already {}, nothing to do", orderId, order.status());
            return Optional.empty();
        }

        Instrument instrument = RowMapping.toInstrument(row);
        AccountRow accountRow = mapper.findAccount(order.accountId());
        if (accountRow == null) {
            throw new UnknownOrderException(orderId,
                    "order references account " + order.accountId() + ", which does not exist");
        }
        Account account = RowMapping.toAccount(accountRow);

        // The checks that need no price. Rejecting here saves a request that
        // the fill path will want later in the day.
        Optional<RejectReason> beforePricing = PreTradeChecks.beforePricing(instrument, account);
        if (beforePricing.isPresent()) {
            return Optional.of(settleAndPublish(
                    new FillDecision.Reject(beforePricing.get()), order));
        }

        Quote quote;
        try {
            quote = quotes.quote(order.symbol());
        } catch (QuoteUnavailableException e) {
            // A BUSINESS OUTCOME, not a message-processing failure.
            log.warn("no price for order {} ({} after {} attempts): rejecting",
                    orderId, e.getMessage(), e.attempts());
            return Optional.of(settleAndPublish(
                    new FillDecision.Reject(RejectReason.NO_PRICE), order));
        }

        FillDecision decision = ruleFor(instrument.assetClass()).decide(order, quote);

        if (decision instanceof FillDecision.Fill fill) {
            // Rules 6 and 7, at the executed price rather than the limit. Both
            // were checked at acceptance against numbers that have since moved.
            Optional<Position> held = Optional.ofNullable(
                            mapper.findPosition(order.accountId(), order.instrumentId(), POSITION_TYPE))
                    .map(RowMapping::toPosition);

            Optional<RejectReason> atExecution =
                    PreTradeChecks.atExecution(order, account, held, fill.executedPrice());

            if (atExecution.isPresent()) {
                return Optional.of(settleAndPublish(
                        new FillDecision.Reject(atExecution.get()), order));
            }

            log.info("order {} is marketable: {} {} of {} at {} (bid {} / ask {}, spread {}bps, source {})",
                    orderId, order.side(), order.quantity(), order.symbol(),
                    fill.executedPrice(), quote.bid(), quote.ask(), quote.spreadBps(), quote.source());
        }

        return Optional.of(settleAndPublish(decision, order));
    }

    /** Settle the order, then — if it was newly settled — publish the trade event. */
    private SettlementResult settleAndPublish(FillDecision decision, OrderSnapshot order) {
        SettlementResult result = settlement.settle(decision, order);
        if (result == SettlementResult.SETTLED) {
            publishTradeEvent(decision, order);
        }
        return result;
    }

    private void publishTradeEvent(FillDecision decision, OrderSnapshot order) {
        Instant eventTime = Instant.now(clock);
        boolean isFill = decision instanceof FillDecision.Fill;

        BigDecimal cashDelta;
        BigDecimal positionQtyAfter = null;
        BigDecimal averageCostAfter = null;

        if (isFill) {
            BigDecimal executedPrice = ((FillDecision.Fill) decision).executedPrice();
            BigDecimal consideration = order.considerationAt(executedPrice);
            cashDelta = order.isBuy() ? consideration.negate() : consideration;

            PositionRow pos = mapper.findPosition(
                    order.accountId(), order.instrumentId(), POSITION_TYPE);
            if (pos != null) {
                positionQtyAfter = pos.getQuantity();
                averageCostAfter = pos.getAveragePrice();
            }
        } else {
            cashDelta = BigDecimal.ZERO;
        }

        TradeEventPayload payload = new TradeEventPayload(
                order.orderId().toString(),
                order.accountId(),
                order.symbol(),
                order.side().name(),
                order.quantity(),
                order.limitPrice(),
                isFill ? ((FillDecision.Fill) decision).executedPrice() : null,
                isFill ? "FILLED" : "REJECTED",
                !isFill ? ((FillDecision.Reject) decision).reason().name() : null,
                cashDelta,
                positionQtyAfter,
                averageCostAfter,
                eventTime);

        EventEnvelope<TradeEventPayload> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                isFill ? "ORDER_FILLED" : "ORDER_REJECTED",
                eventTime,
                "trade-executor",
                1,
                payload);

        tradeEventTemplate.send(tradeEventsTopic, order.accountId().toString(), envelope);
        log.info("trade event {} published for order {} on account {}",
                envelope.eventType(), order.orderId(), order.accountId());
    }

    /** Pricing is per asset class. */
    private FillRule ruleFor(AssetClass assetClass) {
        return assetClass == AssetClass.MUTUAL_FUND ? mutualFundRule : equityRule;
    }
}
