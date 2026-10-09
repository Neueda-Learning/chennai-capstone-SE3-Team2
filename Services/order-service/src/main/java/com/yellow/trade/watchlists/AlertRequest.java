package com.yellow.trade.watchlists;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** A threshold and a direction on one instrument. */
public record AlertRequest(
        @NotBlank @Size(max = 30) String symbol,
        @NotNull AlertDirection direction,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 4) BigDecimal threshold) {
}
