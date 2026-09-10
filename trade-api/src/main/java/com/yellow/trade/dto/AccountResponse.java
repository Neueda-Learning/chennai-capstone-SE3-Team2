package com.yellow.trade.dto;

import com.yellow.enums.AccountStatus;

import java.time.Instant;

/**
 * contracts/trade-api.yaml -> AccountResponse.
 *
 * accountId is the string business reference here, and this is the ONE place
 * in the platform where that name does not mean the numeric key. Everywhere
 * else -- the path variable, PlaceOrderRequest, the JWT claim, every order --
 * accountId is the number.
 *
 * No money: /balance owns that, so a cash figure has one source. No version:
 * no operation in the contract takes it back from a client, and it is
 * internal machinery.
 */
public record AccountResponse(
        String accountId,
        String holderName,
        AccountStatus status,
        Instant openedOn) {
}
