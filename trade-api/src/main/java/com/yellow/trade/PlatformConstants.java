package com.yellow.trade;

/**
 * Facts about this deployment of the platform that more than one layer needs.
 *
 * They live here rather than in whichever class happened to want one first, so
 * that "everything is quoted in rupees" is stated once and a second market
 * changes one file.
 */
public final class PlatformConstants {

    private PlatformConstants() {
    }

    /**
     * ISO 4217 currency of every balance and every quote.
     *
     * A constant rather than a column: the schema holds no currency, because
     * the platform trades one market. The field exists in the contract so that
     * adding a second does not change the shape of a response.
     */
    public static final String QUOTE_CURRENCY = "INR";

    /**
     * Every position this service can create is DELIVERY, because
     * orders.product_type defaults to CNC and no intraday square-off job
     * exists yet. See migration 002_api_alignment.sql.
     */
    public static final String DEFAULT_POSITION_TYPE = "DELIVERY";
}
