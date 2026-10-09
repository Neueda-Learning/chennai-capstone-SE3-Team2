package com.yellow.trade.advice;

import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signals for what the customer holds and watches, as openapi/advice.yaml
 * describes. The caller's own account only; the service checks it first.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountAdviceController {

    private final AccountAdviceService advice;

    public AccountAdviceController(AccountAdviceService advice) {
        this.advice = advice;
    }

    @GetMapping("/{id}/advice")
    public AccountAdvice forAccount(@PathVariable("id") @Min(1) long accountId) {
        return advice.forAccount(accountId);
    }
}
