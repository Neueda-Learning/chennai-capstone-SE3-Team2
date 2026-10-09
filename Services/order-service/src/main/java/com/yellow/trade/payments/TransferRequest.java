package com.yellow.trade.payments;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A deposit or a withdrawal. No bank account in it: money moves only to and
 * from the one registered on the account, so the request cannot name another.
 *
 * @param idempotencyKey the client's key: sending the same transfer again with
 *                       it returns the first one instead of moving money twice
 */
public record TransferRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotBlank @Size(min = 8, max = 64) String idempotencyKey) {
}
