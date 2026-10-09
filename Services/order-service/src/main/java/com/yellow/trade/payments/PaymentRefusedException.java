package com.yellow.trade.payments;

import org.springframework.http.HttpStatus;

/**
 * A transfer refused before anything was written. The code is the catalogue
 * code the UI branches on; the message is for a developer reading a log.
 */
public class PaymentRefusedException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    private PaymentRefusedException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }

    static PaymentRefusedException notEnoughAvailableCash() {
        return new PaymentRefusedException("PAY-400", HttpStatus.BAD_REQUEST, "Not enough available cash");
    }

    static PaymentRefusedException noBankAccount() {
        return new PaymentRefusedException("PAY-404", HttpStatus.NOT_FOUND, "No bank account on file");
    }

    static PaymentRefusedException keyReused() {
        return new PaymentRefusedException("PAY-409", HttpStatus.CONFLICT,
                "Idempotency key already used for a different transfer");
    }
}
