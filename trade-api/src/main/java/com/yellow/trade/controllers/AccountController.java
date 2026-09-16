package com.yellow.trade.controllers;

import com.yellow.enums.OrderStatus;
import com.yellow.trade.dto.AccountResponse;
import com.yellow.trade.dto.BalanceResponse;
import com.yellow.trade.dto.OrderHistoryEntry;
import com.yellow.trade.dto.PositionResponse;
import com.yellow.trade.services.AccountService;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

//The four read operations from contracts/trade-api.yaml.
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable("id") @Min(1) Long accountId) {
        return accountService.getAccount(accountId);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse getBalance(@PathVariable("id") @Min(1) Long accountId) {
        return accountService.getBalance(accountId);
    }

    @GetMapping("/{id}/positions")
    public List<PositionResponse> getPositions(@PathVariable("id") @Min(1) Long accountId) {
        return accountService.getPositions(accountId);
    }

    @GetMapping("/{id}/orders")
    public List<OrderHistoryEntry> getOrders(
            @PathVariable("id") @Min(1) Long accountId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        return accountService.getOrders(accountId, status, from, to);
    }
}
