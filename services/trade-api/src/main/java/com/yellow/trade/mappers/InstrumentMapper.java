package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

// instrument, flattened across its two subtypes.
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

    @Select(SELECT_COLUMNS + " WHERE COALESCE(e.ticker, m.scheme_code) = #{symbol}")
    InstrumentRow findBySymbol(@Param("symbol") String symbol);

    @Select(SELECT_COLUMNS + " WHERE i.instrument_id = #{instrumentId}")
    InstrumentRow findById(@Param("instrumentId") Long instrumentId);

    /**
     * Every instrument that accepts orders, for picking one: the delisted are
     * left out, as the order rules would refuse them. Mutual funds have no
     * exchange.
     */
    @Select("""
            SELECT COALESCE(e.ticker, m.scheme_code) AS symbol,
                   i.name,
                   i.instrument_type,
                   e.exchange_code
            FROM instrument i
            LEFT JOIN equity      e ON e.instrument_id = i.instrument_id
            LEFT JOIN mutual_fund m ON m.instrument_id = i.instrument_id
            WHERE i.is_tradable
              AND COALESCE(e.ticker, m.scheme_code) IS NOT NULL
            ORDER BY symbol
            """)
    List<TradableInstrumentRow> findTradable();
}

