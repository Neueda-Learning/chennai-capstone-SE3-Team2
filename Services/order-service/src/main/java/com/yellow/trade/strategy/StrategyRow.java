package com.yellow.trade.strategy;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of strat_strategy, with its instrument's symbol. */
public class StrategyRow {

    private Long strategyId;
    private Long clientId;
    private Long instrumentId;
    private String symbol;
    private String side;
    private int quantity;
    private String triggerKind;
    private BigDecimal triggerPrice;
    private BigDecimal maxSpend;
    private int maxPosition;
    private boolean enabled;
    private String status;
    private int failures;
    private Instant createdAt;
    private Instant lastFiredAt;

    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getTriggerKind() { return triggerKind; }
    public void setTriggerKind(String triggerKind) { this.triggerKind = triggerKind; }
    public BigDecimal getTriggerPrice() { return triggerPrice; }
    public void setTriggerPrice(BigDecimal triggerPrice) { this.triggerPrice = triggerPrice; }
    public BigDecimal getMaxSpend() { return maxSpend; }
    public void setMaxSpend(BigDecimal maxSpend) { this.maxSpend = maxSpend; }
    public int getMaxPosition() { return maxPosition; }
    public void setMaxPosition(int maxPosition) { this.maxPosition = maxPosition; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getFailures() { return failures; }
    public void setFailures(int failures) { this.failures = failures; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastFiredAt() { return lastFiredAt; }
    public void setLastFiredAt(Instant lastFiredAt) { this.lastFiredAt = lastFiredAt; }

    Strategy toStrategy() {
        return toStrategy(null);
    }

    /** With what an indicator strategy waits on, or null. */
    Strategy toStrategy(StrategyIndicator indicator) {
        return new Strategy(strategyId, symbol, com.yellow.enums.OrderSide.valueOf(side), quantity,
                Trigger.valueOf(triggerKind), triggerPrice, maxSpend, maxPosition, enabled,
                StrategyStatus.valueOf(status), failures, createdAt, lastFiredAt, indicator);
    }
}
