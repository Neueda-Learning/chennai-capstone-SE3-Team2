package com.yellow.executor.quotes;

import java.util.List;
import java.util.Map;

/**
 * Where prices come from.
 *
 * <p>A port rather than a concrete client, for two reasons. The fill path's
 * interesting cases are "the price was stale", "the API was down" and "the
 * symbol is unknown", and driving those through a real HTTP client in every
 * test would make them slow and flaky. And an instrument class this venue
 * cannot price today -- a mutual fund, which would need a NAV source rather
 * than a quote -- becomes a second implementation behind this interface rather
 * than an edit to the executor.
 */
public interface QuoteSource {

    /**
     * One quote, for the fill path.
     *
     * <p>Retries internally for anything that might succeed on a second ask,
     * and throws once that budget is spent. The caller does not retry: it
     * rejects the order.
     *
     * @throws QuoteUnavailableException when no usable price could be obtained
     */
    Quote quote(String symbol);

    /**
     * Up to 25 quotes in one request, for the poller.
     *
     * <p>Batching is a quota optimisation: the whole call costs one request
     * whatever the symbol count. A symbol the API could not serve is simply
     * absent from the result rather than failing the batch, because one bad
     * symbol must not cost every other symbol its update.
     */
    Map<String, Quote> quotes(List<String> symbols);
}
