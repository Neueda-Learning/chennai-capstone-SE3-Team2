package com.yellow.trade.portfolio;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * The three portfolio routes of Contracts/API Schemas/portfolio-api.yaml, binding. Under
 * /api/v1/, so the token filter has verified the token; the service compares
 * the account claim with the path before it reads anything.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
@Validated
public class PortfolioController {

    private final PortfolioService portfolio;

    public PortfolioController(PortfolioService portfolio) {
        this.portfolio = portfolio;
    }

    @GetMapping("/{accountId}")
    public PortfolioSummary summary(@PathVariable("accountId") @Min(1) long accountId) {
        return portfolio.summary(accountId);
    }

    @GetMapping("/{accountId}/positions")
    public List<PricedPosition> positions(@PathVariable("accountId") @Min(1) long accountId,
                                          @RequestParam(name = "symbol", required = false) @Size(max = 20) String symbol) {
        return portfolio.positions(accountId, symbol);
    }

    @GetMapping("/{accountId}/pnl")
    public PnlResponse pnl(@PathVariable("accountId") @Min(1) long accountId,
                           @RequestParam(name = "from", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(name = "to", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(name = "bySymbol", defaultValue = "false") boolean bySymbol) {
        return portfolio.pnl(accountId, from, to, bySymbol);
    }
}
