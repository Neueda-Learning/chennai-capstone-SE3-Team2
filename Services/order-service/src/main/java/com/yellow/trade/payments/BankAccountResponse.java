package com.yellow.trade.payments;

/**
 * The registered bank account, masked: the last four digits are enough for
 * the customer to recognise it, and the full number never leaves the server.
 */
public record BankAccountResponse(String accountNumberLast4, String ifsc, String holderName) {
}
