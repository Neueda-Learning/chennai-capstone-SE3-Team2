package com.yellow.executor.persistence;

import com.yellow.entities.Account;
import com.yellow.entities.Instrument;
import com.yellow.entities.Position;
import com.yellow.enums.AccountStatus;
import com.yellow.enums.AssetClass;
import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.executor.fill.OrderSnapshot;

/**
 * Rows to domain types, in one place at the edge of persistence.
 *
 * <p>Past this class nothing handles a string where an enum belongs or a bean
 * where an entity belongs. The checks take {@link Account} and
 * {@link Instrument} precisely so they can call the rules the domain already
 * has -- {@code canAfford} is Sprint 5's rule 6 and {@code canSell} is rule 7 --
 * and that only works if the row is converted rather than passed along.
 *
 * <p>The vocabulary is the same on both sides: the database stores the
 * contract's literals, enforced by check constraints, and the enums spell them
 * identically. So the conversions are {@code valueOf} rather than a translation
 * table, and a mismatch is a schema bug that fails loudly rather than a value
 * quietly mapping to a default.
 */
public final class RowMapping {

    private RowMapping() {
    }

    public static OrderSnapshot toSnapshot(ExecutableOrderRow row) {
        return new OrderSnapshot(
                row.getOrderId(),
                row.getClientId(),
                row.getInstrumentId(),
                row.getSymbol(),
                OrderSide.valueOf(row.getSide()),
                row.getQuantity(),
                row.getPrice(),
                OrderStatus.valueOf(row.getStatus()));
    }

    public static Instrument toInstrument(ExecutableOrderRow row) {
        return new Instrument(
                row.getInstrumentId(),
                row.getSymbol(),
                row.getInstrumentName(),
                assetClass(row.getInstrumentType()),
                // The platform quotes in rupees. Fauxnance reports the currency
                // per quote and we carry it onto market-data, but the account's
                // cash is INR and nothing here converts.
                "INR",
                row.isTradable());
    }

    public static Account toAccount(AccountRow row) {
        return new Account(
                row.getClientId(),
                row.getAccountRef(),
                row.getClientId(),
                row.getBalance(),
                row.getBlockedFunds(),
                AccountStatus.valueOf(row.getStatus()),
                row.getVersion());
    }

    public static Position toPosition(PositionRow row) {
        return new Position(
                row.getPositionId(),
                row.getClientId(),
                row.getInstrumentId(),
                row.getQuantity(),
                row.getAveragePrice());
    }

    /**
     * The schema's instrument_type against the domain's AssetClass. The two
     * disagree on spelling only for funds, where the column says MF and the
     * enum says MUTUAL_FUND.
     */
    private static AssetClass assetClass(String instrumentType) {
        return switch (instrumentType) {
            case "STOCK" -> AssetClass.EQUITY;
            case "ETF" -> AssetClass.ETF;
            case "MF" -> AssetClass.MUTUAL_FUND;
            default -> throw new IllegalStateException(
                    "unknown instrument_type '" + instrumentType + "'. The column is "
                            + "constrained to STOCK, ETF and MF, so this means the schema "
                            + "moved without the executor.");
        };
    }
}
