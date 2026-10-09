package com.yellow.trade.watchlists;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The customer's watchlists, as openapi/watchlists.yaml describes. Under
 * /api/v1/, so the token filter has verified the token; the service decides
 * whether this caller may reach this account and this watchlist.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class WatchlistsController {

    private final WatchlistService watchlists;

    public WatchlistsController(WatchlistService watchlists) {
        this.watchlists = watchlists;
    }

    @GetMapping("/{id}/watchlists")
    public List<Watchlist> list(@PathVariable("id") @Min(1) long accountId) {
        return watchlists.list(accountId);
    }

    @PostMapping("/{id}/watchlists")
    @ResponseStatus(HttpStatus.CREATED)
    public Watchlist create(@PathVariable("id") @Min(1) long accountId, @Valid @RequestBody WatchlistRequest request) {
        return watchlists.create(accountId, request.name());
    }

    @PutMapping("/{id}/watchlists/{watchlistId}")
    public Watchlist rename(@PathVariable("id") @Min(1) long accountId,
                            @PathVariable("watchlistId") @Min(1) long watchlistId,
                            @Valid @RequestBody WatchlistRequest request) {
        return watchlists.rename(accountId, watchlistId, request.name());
    }

    @DeleteMapping("/{id}/watchlists/{watchlistId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") @Min(1) long accountId, @PathVariable("watchlistId") @Min(1) long watchlistId) {
        watchlists.delete(accountId, watchlistId);
    }

    @PutMapping("/{id}/watchlists/{watchlistId}/items/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addItem(@PathVariable("id") @Min(1) long accountId, @PathVariable("watchlistId") @Min(1) long watchlistId,
                        @PathVariable("symbol") @Size(max = 30) String symbol) {
        watchlists.addItem(accountId, watchlistId, symbol);
    }

    @DeleteMapping("/{id}/watchlists/{watchlistId}/items/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@PathVariable("id") @Min(1) long accountId,
                           @PathVariable("watchlistId") @Min(1) long watchlistId,
                           @PathVariable("symbol") @Size(max = 30) String symbol) {
        watchlists.removeItem(accountId, watchlistId, symbol);
    }
}
