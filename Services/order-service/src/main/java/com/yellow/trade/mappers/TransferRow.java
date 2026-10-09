package com.yellow.trade.mappers;

import java.math.BigDecimal;
import java.time.Instant;

/** One fund_transfer row: a deposit or a withdrawal. */
public class TransferRow {

    private long transferId;
    private long clientId;
    private String direction;
    private BigDecimal amount;
    private String status;
    private String reason;
    private Instant createdAt;
    private Instant decidedAt;

    public long getTransferId() { return transferId; }
    public void setTransferId(long transferId) { this.transferId = transferId; }
    public long getClientId() { return clientId; }
    public void setClientId(long clientId) { this.clientId = clientId; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
