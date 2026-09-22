package com.yellow.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error response envelope for API failures")
public record ErrorResponse(
        @Schema(description = "Error code identifier (format: DOMAIN-HTTP_STATUS, e.g., ORD-409)", example = "ORD-409")
        String errorCode,
        @Schema(description = "Human-readable error message", example = "Duplicate order: idempotency key already processed")
        String message) {
}
