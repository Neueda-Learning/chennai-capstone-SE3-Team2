package com.yellow.trade.watchlists;

import java.math.BigDecimal;
import java.time.Instant;

/** One watch_item, with its instrument's symbol and the latest quote held for it. */
public class ItemRow {

    private Long watchlistId;
    private String symbol;
    private int position;
    private BigDecimal lastPrice;
    private BigDecimal changePercent;
    private Instant priceAsOf;
    private boolean stale;

    public Long getWatchlistId() { return watchlistId; }
    public void setWatchlistId(Long watchlistId) { this.watchlistId = watchlistId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public BigDecimal getLastPrice() { return lastPrice; }
    public void setLastPrice(BigDecimal lastPrice) { this.lastPrice = lastPrice; }
    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }
    public Instant getPriceAsOf() { return priceAsOf; }
    public void setPriceAsOf(Instant priceAsOf) { this.priceAsOf = priceAsOf; }
    public boolean isStale() { return stale; }
    public void setStale(boolean stale) { this.stale = stale; }
}
