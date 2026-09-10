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

/**
 * The four read operations from contracts/trade-api.yaml.
 *
 * This class speaks HTTP and nothing else: paths, verbs, DTOs, validation
 * annotations. It holds no SQL, opens no transaction and makes no decision
 * about who may see what -- the service answers that, where the account key
 * has been resolved against the database.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@PathVariable @Min(1) Long accountId) {
        return accountService.getAccount(accountId);
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable @Min(1) Long accountId) {
        return accountService.getBalance(accountId);
    }

    @GetMapping("/{accountId}/positions")
    public List<PositionResponse> getPositions(@PathVariable @Min(1) Long accountId) {
        return accountService.getPositions(accountId);
    }

    /**
     * The three filters are optional and bound as typed parameters, not as
     * text. An unparseable status or timestamp is refused by Spring before
     * this method runs and leaves as VAL-422 -- it never reaches a statement.
     */
    @GetMapping("/{accountId}/orders")
    public List<OrderHistoryEntry> getOrderHistory(
            @PathVariable @Min(1) Long accountId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        return accountService.getOrders(accountId, status, from, to);
    }
}
