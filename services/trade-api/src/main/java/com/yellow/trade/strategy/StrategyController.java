package com.yellow.trade.strategy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
 * The customer's strategies, as openapi/strategy.yaml describes. Under
 * /api/v1/, so the token filter has verified the token; the service decides
 * whether this caller may reach this account and this strategy. Nothing here
 * places an order: firing happens on the stream, through the order route.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class StrategyController {

    private final StrategyService strategies;

    public StrategyController(StrategyService strategies) {
        this.strategies = strategies;
    }

    @GetMapping("/{id}/strategies")
    public List<Strategy> list(@PathVariable("id") @Min(1) long accountId) {
        return strategies.list(accountId);
    }

    @PostMapping("/{id}/strategies")
    @ResponseStatus(HttpStatus.CREATED)
    public Strategy create(@PathVariable("id") @Min(1) long accountId, @Valid @RequestBody StrategyRequest request) {
        return strategies.create(accountId, request);
    }

    @DeleteMapping("/{id}/strategies/{strategyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") @Min(1) long accountId, @PathVariable("strategyId") @Min(1) long strategyId) {
        strategies.delete(accountId, strategyId);
    }

    @PutMapping("/{id}/strategies/{strategyId}/enabled")
    public Strategy setEnabled(@PathVariable("id") @Min(1) long accountId,
                               @PathVariable("strategyId") @Min(1) long strategyId,
                               @Valid @RequestBody EnabledRequest request) {
        return strategies.setEnabled(accountId, strategyId, request.enabled());
    }

    @GetMapping("/{id}/strategies/{strategyId}/runs")
    public List<StrategyRun> runs(@PathVariable("id") @Min(1) long accountId,
                                  @PathVariable("strategyId") @Min(1) long strategyId) {
        return strategies.runs(accountId, strategyId);
    }
}
