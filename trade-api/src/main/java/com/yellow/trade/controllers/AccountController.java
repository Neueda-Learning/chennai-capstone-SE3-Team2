package com.yellow.trade.controllers;

import com.yellow.enums.OrderStatus;
import com.yellow.trade.dto.AccountResponse;
import com.yellow.trade.dto.BalanceResponse;
import com.yellow.trade.dto.OrderHistoryEntry;
import com.yellow.trade.dto.PositionResponse;
import com.yellow.trade.services.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Accounts", description = "Account management and queries")
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Get account details", description = "Retrieve account information including holder name, cash balance, and status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Account found", content = @Content(schema = @Schema(implementation = AccountResponse.class))),
        @ApiResponse(responseCode = "403", description = "Account access denied (ACC-403)"),
        @ApiResponse(responseCode = "404", description = "Account not found (ACC-404)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}")
    public AccountResponse getAccount(
            @Parameter(description = "Account ID", required = true)
            @PathVariable("id") @Min(1) Long accountId) {
        return accountService.getAccount(accountId);
    }

    @Operation(summary = "Get account balance", description = "Retrieve current cash balance for the account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Balance retrieved", content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
        @ApiResponse(responseCode = "403", description = "Account access denied (ACC-403)"),
        @ApiResponse(responseCode = "404", description = "Account not found (ACC-404)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}/balance")
    public BalanceResponse getBalance(
            @Parameter(description = "Account ID", required = true)
            @PathVariable("id") @Min(1) Long accountId) {
        return accountService.getBalance(accountId);
    }

    @Operation(summary = "Get account positions", description = "Retrieve list of all positions (holdings) for the account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Positions retrieved", content = @Content(schema = @Schema(type = "array", implementation = PositionResponse.class))),
        @ApiResponse(responseCode = "403", description = "Account access denied (ACC-403)"),
        @ApiResponse(responseCode = "404", description = "Account not found (ACC-404)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}/positions")
    public List<PositionResponse> getPositions(
            @Parameter(description = "Account ID", required = true)
            @PathVariable("id") @Min(1) Long accountId) {
        return accountService.getPositions(accountId);
    }

    @Operation(summary = "Get order history", description = "Retrieve order history for the account with optional filtering by status and date range")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Orders retrieved", content = @Content(schema = @Schema(type = "array", implementation = OrderHistoryEntry.class))),
        @ApiResponse(responseCode = "403", description = "Account access denied (ACC-403)"),
        @ApiResponse(responseCode = "404", description = "Account not found (ACC-404)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/{id}/orders")
    public List<OrderHistoryEntry> getOrders(
            @Parameter(description = "Account ID", required = true)
            @PathVariable("id") @Min(1) Long accountId,
            @Parameter(description = "Order status filter (optional)")
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Start date (ISO 8601 format, optional)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "End date (ISO 8601 format, optional)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        return accountService.getOrders(accountId, status, from, to);
    }
}
