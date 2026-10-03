package com.yellow.trade.payments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cash in and out. Under /api/v1/, so the token filter guards every route; not
 * in contracts/trade-api.yaml -- described in our own extension contract.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping("/{id}/bank-account")
    public BankAccountResponse bankAccount(@PathVariable("id") @Min(1) Long accountId) {
        return payments.bankAccount(accountId);
    }

    @GetMapping("/{id}/transfers")
    public List<TransferResponse> transfers(@PathVariable("id") @Min(1) Long accountId) {
        return payments.transfers(accountId);
    }

    /** 202: accepted and PENDING; the gateway decides shortly. */
    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferResponse deposit(@PathVariable("id") @Min(1) Long accountId,
                                    @Valid @RequestBody TransferRequest request) {
        return payments.deposit(accountId, request);
    }

    /** 202: accepted, the amount held, and PENDING; the gateway decides shortly. */
    @PostMapping("/{id}/withdrawals")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferResponse withdraw(@PathVariable("id") @Min(1) Long accountId,
                                     @Valid @RequestBody TransferRequest request) {
        return payments.withdraw(accountId, request);
    }
}
