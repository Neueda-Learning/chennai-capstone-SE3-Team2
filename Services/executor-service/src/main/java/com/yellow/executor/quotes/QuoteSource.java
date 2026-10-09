package com.yellow.executor.quotes;

import java.util.List;
import java.util.Map;

/** Where prices come from. A port rather than a concrete client, for two reasons. */
public interface QuoteSource {

    /**
     * One quote, for the fill path. Retries internally for anything that might
     * succeed on a second ask, and throws once that budget is spent.
     */
    Quote quote(String symbol);

    /**
     * Up to 25 quotes in one request, for the poller. Batching is a quota
     * optimisation: the whole call costs one request whatever the symbol
     * count.
     */
    Map<String, Quote> quotes(List<String> symbols);
}
