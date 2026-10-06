package com.yellow.trade.marketdata;

/**
 * No price source answered usefully. Answered as 503 MKT-503, the code and
 * message contracts/portfolio-api.yaml gives it. Only the market-data routes
 * raise it; the Sprint 6 contract routes never price anything.
 */
public class PricingUnavailableException extends RuntimeException {

    public static final String CODE = "MKT-503";
    public static final String PUBLIC_MESSAGE = "Pricing unavailable";

    public PricingUnavailableException(String message) {
        super(message);
    }

    public PricingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
