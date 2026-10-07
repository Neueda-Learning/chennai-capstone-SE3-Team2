package com.yellow.trade.watchlists;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of watch_alert, with its instrument's symbol. */
public class AlertRow {

    private Long alertId;
    private Long clientId;
    private Long instrumentId;
    private String symbol;
    private String direction;
    private BigDecimal threshold;
    private String status;
    private Instant createdAt;
    private Instant triggeredAt;
    private BigDecimal triggeredPrice;
    private UUID notificationId;

    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }
    public BigDecimal getTriggeredPrice() { return triggeredPrice; }
    public void setTriggeredPrice(BigDecimal triggeredPrice) { this.triggeredPrice = triggeredPrice; }
    public UUID getNotificationId() { return notificationId; }
    public void setNotificationId(UUID notificationId) { this.notificationId = notificationId; }

    PriceAlert toAlert() {
        return new PriceAlert(alertId, symbol, AlertDirection.valueOf(direction), threshold, AlertStatus.valueOf(status),
                createdAt, triggeredAt, triggeredPrice, notificationId);
    }
}
