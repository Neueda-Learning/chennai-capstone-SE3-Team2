package com.yellow.trade.controllers;

import com.yellow.enums.OrderStatus;
import com.yellow.trade.dto.AccountResponse;
import com.yellow.trade.dto.BalanceResponse;
import com.yellow.trade.dto.OrderHistoryEntry;
import com.yellow.trade.dto.PositionResponse;
import com.yellow.trade.services.AccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/api/v1/accounts/{id}")
    public AccountResponse getAccount(@PathVariable Long id) {
        return accountService.getAccount(id);
    }

    @GetMapping("/api/v1/accounts/{id}/balance")
    public BalanceResponse getBalance(@PathVariable Long id) {
        return accountService.getBalance(id);
    }

    @GetMapping("/api/v1/accounts/{id}/positions")
    public List<PositionResponse> getPositions(@PathVariable Long id) {
        return accountService.getPositions(id);
    }

    @GetMapping("/api/v1/accounts/{id}/orders")
    public List<OrderHistoryEntry> getOrders(
            @PathVariable Long id,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return accountService.getOrders(id, status, from, to);
    }
}