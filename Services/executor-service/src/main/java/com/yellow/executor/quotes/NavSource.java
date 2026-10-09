package com.yellow.executor.quotes;

/**
 * Where fund NAVs come from. Not a QuoteSource: a NAV is one price a day, the
 * same both ways, from a different service, and the poller has no use for it.
 */
public interface NavSource {

    /**
     * The latest NAV for an AMFI scheme code or an ISIN, as a quote whose bid
     * and ask are both the NAV. Retries anything that might succeed on a
     * second ask, and throws once that budget is spent or there is no usable
     * NAV at all.
     *
     * @throws QuoteUnavailableException when there is no usable NAV
     */
    Quote nav(String identifier);
}
