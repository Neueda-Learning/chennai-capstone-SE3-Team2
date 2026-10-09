package com.yellow.trade.portfolio.api;

import java.util.List;

/**
 * What an account holds, published by the portfolio module for the modules
 * that read it (advice). Read-only. The caller has already checked that the
 * account is the caller's own.
 */
public interface Holdings {

    /** The symbols the account holds now, quantity above zero, each once, in symbol order. */
    List<String> heldSymbols(long accountId);
}
