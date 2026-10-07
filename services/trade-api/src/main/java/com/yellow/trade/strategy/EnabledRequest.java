package com.yellow.trade.strategy;

import jakarta.validation.constraints.NotNull;

/** Switch a strategy on, or off at once. */
public record EnabledRequest(@NotNull Boolean enabled) {
}
