package com.yellow.exceptions;

import com.yellow.enums.Reason;

public class InstrumentNotFoundException extends TradeException {

    private final String requestedSymbol;
    private final Reason reason;

    public InstrumentNotFoundException(String requestedSymbol, Reason reason) {
        super("INS-404", "Instrument not found");
        this.requestedSymbol = requestedSymbol;
        this.reason = reason;
    }

    public String requestedSymbol() {
        return requestedSymbol;
    }

    public Reason reason() {
        return reason;
    }

    public InstrumentNotFoundException() {
        this(null, null);
    }
}
