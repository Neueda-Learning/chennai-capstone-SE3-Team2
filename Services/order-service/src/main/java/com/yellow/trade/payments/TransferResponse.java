package com.yellow.trade.payments;

import com.yellow.trade.mappers.TransferRow;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A transfer as the customer sees it. PENDING until the gateway decides;
 * then SUCCESS, or FAILED with the reason.
 */
public record TransferResponse(
        long transferId,
        String direction,
        BigDecimal amount,
        String status,
        String reason,
        Instant createdAt,
        Instant decidedAt) {

    static TransferResponse of(TransferRow row) {
        return new TransferResponse(row.getTransferId(), row.getDirection(), row.getAmount(),
                row.getStatus(), row.getReason(), row.getCreatedAt(), row.getDecidedAt());
    }
}
