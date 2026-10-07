package com.yellow.trade.strategy;

import com.yellow.enums.OrderSide;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A strategy to create: created disabled, so nothing fires until the customer switches it on.
 * A falls-to or rises-to trigger needs a price; an indicator trigger takes none (the service
 * says VAL-422 either way).
 */
public record StrategyRequest(
        @NotBlank @Size(max = 30) String symbol,
        @NotNull OrderSide side,
        @NotNull @Min(1) Integer quantity,
        @NotNull Trigger trigger,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 2) BigDecimal triggerPrice,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 4) BigDecimal maxSpend,
        @NotNull @Min(1) Integer maxPosition) {
}
