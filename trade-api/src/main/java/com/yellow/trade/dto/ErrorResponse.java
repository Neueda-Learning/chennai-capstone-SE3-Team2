package com.yellow.trade.dto;

// contract: ErrorResponse, additionalProperties false, required [errorCode, message]
// this is the ONE body shape every failure returns - no whitelabel page, no bare status
public class ErrorResponse {

    private final String errorCode;
    private final String message;

    public ErrorResponse(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }
}