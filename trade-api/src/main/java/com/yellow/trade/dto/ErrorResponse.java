package com.yellow.trade.dto;

/**
 * contracts/trade-api.yaml -> ErrorResponse.
 *
 * The one body shape every failure returns: no whitelabel page, no stack
 * trace, no bare status with an empty body. Sprint 9 keeps one error handler
 * because there is one envelope.
 *
 * Clients branch on errorCode, never on the status alone -- 404 carries both
 * ACC-404 and INS-404, and 409 carries insufficient holdings, a reused
 * idempotency key and an uncancellable order.
 *
 * message is written for a human reading a screen: no class name, no SQL
 * fragment, no account key, no internal identifier. What an investigation
 * needs is logged on the server instead. Leaking it here is OWASP A05.
 */
public record ErrorResponse(String errorCode, String message) {
}
