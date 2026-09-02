package com.yellow.exceptions;

public class InstrumentNotFoundException extends TradeException {

    public InstrumentNotFoundException() {
        super("INS-404", "Instrument not found");
    }
}