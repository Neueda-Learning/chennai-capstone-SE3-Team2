package com.yellow.trade.watchlists;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A watchlist's name, to create or rename one. */
public record WatchlistRequest(@NotBlank @Size(max = 40) String name) {
}
