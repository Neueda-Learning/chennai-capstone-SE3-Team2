package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * instrument, flattened across its two subtypes.
 *
 * The schema models instruments as a supertype with disjoint subtypes: a
 * stock or ETF has a ticker on equity, a fund has a scheme_code on
 * mutual_fund. The contract exposes one "symbol" field, so both joins are
 * left joins and COALESCE picks whichever subtype the row actually has.
 */
@Mapper
public interface InstrumentMapper {

    String SELECT_COLUMNS = """
            SELECT i.instrument_id,
                   COALESCE(e.ticker, m.scheme_code) AS symbol,
                   i.name,
                   i.instrument_type,
                   i.is_tradable AS tradable
            FROM instrument i
            LEFT JOIN equity      e ON e.instrument_id = i.instrument_id
            LEFT JOIN mutual_fund m ON m.instrument_id = i.instrument_id
            """;

    /**
     * Tradability is deliberately NOT filtered here. Rule 3 distinguishes an
     * unknown symbol from a known one that stopped trading, and both raise
     * INS-404 -- but the domain decides that, not this statement. A query
     * that hid delisted rows would also hide the holdings a client still has
     * in them.
     */
    @Select(SELECT_COLUMNS + " WHERE COALESCE(e.ticker, m.scheme_code) = #{symbol}")
    InstrumentRow findBySymbol(@Param("symbol") String symbol);

    @Select(SELECT_COLUMNS + " WHERE i.instrument_id = #{instrumentId}")
    InstrumentRow findById(@Param("instrumentId") Long instrumentId);
}
