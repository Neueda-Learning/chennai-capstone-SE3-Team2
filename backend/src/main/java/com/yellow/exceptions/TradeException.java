package com.yellow.exceptions;

// base exception
public class TradeException extends RuntimeException {
    private final String code;
    public TradeException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String getCode() {
        return code;
    }
}