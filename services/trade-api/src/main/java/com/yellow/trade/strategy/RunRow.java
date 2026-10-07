package com.yellow.trade.strategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of strat_run. */
public class RunRow {

    private Long runId;
    private Long strategyId;
    private Instant at;
    private BigDecimal quotePrice;
    private String outcome;
    private String reason;
    private UUID orderId;
    private UUID sourceEventId;

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
    public BigDecimal getQuotePrice() { return quotePrice; }
    public void setQuotePrice(BigDecimal quotePrice) { this.quotePrice = quotePrice; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(UUID sourceEventId) { this.sourceEventId = sourceEventId; }

    static RunRow of(long strategyId, RunOutcome outcome, java.math.BigDecimal quotePrice, String reason,
                     UUID orderId, UUID sourceEventId, Instant at) {
        RunRow row = new RunRow();
        row.setStrategyId(strategyId);
        row.setOutcome(outcome.name());
        row.setQuotePrice(quotePrice);
        row.setReason(reason);
        row.setOrderId(orderId);
        row.setSourceEventId(sourceEventId);
        row.setAt(at);
        return row;
    }

    StrategyRun toRun() {
        return new StrategyRun(runId, at, quotePrice, RunOutcome.valueOf(outcome), reason);
    }
}
