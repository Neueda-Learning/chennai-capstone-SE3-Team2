package com.yellow.trade.watchlists;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The customer's price alerts, as openapi/watchlists.yaml describes. Firing happens on the stream, never here. */
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AlertsController {

    private final AlertService alerts;

    public AlertsController(AlertService alerts) {
        this.alerts = alerts;
    }

    @GetMapping("/{id}/alerts")
    public List<PriceAlert> list(@PathVariable("id") @Min(1) long accountId) {
        return alerts.list(accountId);
    }

    @PostMapping("/{id}/alerts")
    @ResponseStatus(HttpStatus.CREATED)
    public PriceAlert create(@PathVariable("id") @Min(1) long accountId, @Valid @RequestBody AlertRequest request) {
        return alerts.create(accountId, request);
    }

    @DeleteMapping("/{id}/alerts/{alertId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable("id") @Min(1) long accountId, @PathVariable("alertId") @Min(1) long alertId) {
        alerts.cancel(accountId, alertId);
    }

    @PostMapping("/{id}/alerts/{alertId}/rearm")
    public PriceAlert rearm(@PathVariable("id") @Min(1) long accountId, @PathVariable("alertId") @Min(1) long alertId) {
        return alerts.rearm(accountId, alertId);
    }
}
