package com.yellow.trade.watchlists.api;

import java.util.List;

/**
 * What an account watches, published by the watchlists module for the
 * modules that read it (advice). Read-only. The caller has already checked
 * that the account is the caller's own.
 */
public interface WatchedInstruments {

    /** Every symbol on the account's watchlists, in watchlist and then position order, each once. */
    List<String> watchedSymbols(long accountId);
}
