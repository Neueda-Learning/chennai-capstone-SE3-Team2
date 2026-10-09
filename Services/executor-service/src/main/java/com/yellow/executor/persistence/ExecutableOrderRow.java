package com.yellow.executor.persistence;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of ExecutionMapper#findOrder: the order joined to the facts about
 * its instrument that the checks need.
 */
public class ExecutableOrderRow {

    private UUID orderId;
    private Long clientId;
    private Long instrumentId;
    private String symbol;
    private String side;
    private BigDecimal quantity;
    private BigDecimal price;
    private String status;
    private String instrumentType;
    private String instrumentName;
    private boolean tradable;

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }

    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    /** The LIMIT price the customer submitted, not a fill price. */
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /** STOCK, ETF or MF. */
    public String getInstrumentType() { return instrumentType; }
    public void setInstrumentType(String instrumentType) { this.instrumentType = instrumentType; }

    public String getInstrumentName() { return instrumentName; }
    public void setInstrumentName(String instrumentName) { this.instrumentName = instrumentName; }

    public boolean isTradable() { return tradable; }
    public void setTradable(boolean tradable) { this.tradable = tradable; }
}
