package com.yellow.trade.persistence;

import com.yellow.entities.Account;
import com.yellow.entities.Instrument;
import com.yellow.entities.Order;
import com.yellow.entities.Position;
import com.yellow.enums.AssetClass;
import com.yellow.trade.PlatformConstants;
import com.yellow.trade.mappers.AccountRow;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.mappers.OrderRow;
import com.yellow.trade.mappers.PositionRow;

/**
 * The one place a database row becomes a domain object and back.
 *
 * It lives here rather than on either side of the boundary on purpose. The
 * domain must not know that rows exist, and a mapper must not decide what a
 * row means -- so the translation is its own thing, and the vocabulary
 * mismatches between the schema and the model are all visible in one file
 * instead of scattered across four adapters.
 */
final class RowMapping {

    private RowMapping() {
    }

    static Account toAccount(AccountRow row) {
        // The domain distinguishes the account key from the owning client id.
        // This schema does not: client_account.client_id is both, because one
        // client holds exactly one account. Passing it twice records that
        // deliberately rather than inventing a second identifier.
        return new Account(
                row.getClientId(),
                row.getAccountRef(),
                row.getClientId(),
                row.getBalance(),
                row.getBlockedFunds(),
                row.getStatus(),
                row.getVersion());
    }

    static Instrument toInstrument(InstrumentRow row) {
        return new Instrument(
                row.getInstrumentId(),
                row.getSymbol(),
                row.getName(),
                assetClassOf(row.getInstrumentType()),
                PlatformConstants.QUOTE_CURRENCY,
                row.isTradable());
    }

    /**
     * The schema's instrument_type and the domain's AssetClass name the same
     * three things differently. STOCK is the schema's word for a listed
     * share; EQUITY is the domain's.
     */
    private static AssetClass assetClassOf(String instrumentType) {
        return switch (instrumentType) {
            case "STOCK" -> AssetClass.EQUITY;
            case "ETF"   -> AssetClass.ETF;
            case "MF"    -> AssetClass.MUTUAL_FUND;
            default -> throw new IllegalStateException(
                    "instrument_type outside the values ck_instrument_type permits");
        };
    }

    static Position toPosition(PositionRow row) {
        return new Position(
                row.getPositionId(),
                row.getClientId(),
                row.getInstrumentId(),
                row.getQuantity(),
                row.getAveragePrice());
    }

    static Order toOrder(OrderRow row) {
        return new Order(
                row.getOrderId(),
                row.getClientId(),
                row.getInstrumentId(),
                row.getSide(),
                row.getQuantity(),
                row.getPrice(),
                row.getFillPrice(),
                row.getStatus(),
                row.getIdempotencyKey(),
                row.getDatePlaced(),
                row.getResolvedAt());
    }

    static OrderRow toRow(Order order) {
        OrderRow row = new OrderRow();
        row.setOrderId(order.orderId());
        row.setClientId(order.accountId());
        row.setInstrumentId(order.instrumentId());
        row.setSide(order.side());
        row.setPrice(order.limitPrice());
        row.setQuantity(order.quantity());
        row.setFillPrice(order.executedPrice());
        row.setStatus(order.status());
        row.setIdempotencyKey(order.idempotencyKey());
        row.setDatePlaced(order.placedAt());
        row.setResolvedAt(order.resolvedAt());
        // order_type and product_type are left to their column defaults.
        return row;
    }
}
