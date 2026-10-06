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

    /** Columns for a picker or a label, flattened across the two subtypes. */
    String LISTING = """
            SELECT COALESCE(e.ticker, m.scheme_code) AS symbol,
                   i.name,
                   i.instrument_type,
                   e.exchange_code,
                   i.is_tradable AS tradable
            FROM instrument i
            LEFT JOIN equity      e ON e.instrument_id = i.instrument_id
            LEFT JOIN mutual_fund m ON m.instrument_id = i.instrument_id
            """;

    /**
     * Every instrument that accepts orders, for picking one: the delisted are
     * left out, as the order rules would refuse them. Mutual funds have no
     * exchange.
     *
     * @param type STOCK, ETF or MF; null for every type
     */
    @Select("<script>" + LISTING + """
            WHERE i.is_tradable
              AND COALESCE(e.ticker, m.scheme_code) IS NOT NULL
              <if test="type != null">AND i.instrument_type = #{type}</if>
            ORDER BY symbol
            </script>""")
    List<TradableInstrumentRow> findTradable(@Param("type") String type);

    /**
     * Tradable instruments whose symbol or name matches, best first: the exact
     * symbol, symbols starting with the text, names starting with it, then names
     * containing it. Matching ignores case; the patterns arrive escaped.
     */
    @Select("<script>" + LISTING + """
            WHERE i.is_tradable
              AND COALESCE(e.ticker, m.scheme_code) IS NOT NULL
              <if test="type != null">AND i.instrument_type = #{type}</if>
              AND (UPPER(COALESCE(e.ticker, m.scheme_code)) LIKE #{startsWith} ESCAPE '!'
                   OR UPPER(i.name) LIKE #{contains} ESCAPE '!')
            ORDER BY CASE
                         WHEN UPPER(COALESCE(e.ticker, m.scheme_code)) = #{exact} THEN 0
                         WHEN UPPER(COALESCE(e.ticker, m.scheme_code)) LIKE #{startsWith} ESCAPE '!' THEN 1
                         WHEN UPPER(i.name) LIKE #{startsWith} ESCAPE '!' THEN 2
                         ELSE 3
                     END,
                     i.name
            LIMIT #{limit}
            </script>""")
    List<TradableInstrumentRow> searchMatching(@Param("exact") String exact,
                                               @Param("startsWith") String startsWith,
                                               @Param("contains") String contains,
                                               @Param("type") String type,
                                               @Param("limit") int limit);

    /**
     * Search by text a customer typed. % and _ in it are matched as themselves,
     * never as wildcards.
     */
    default List<TradableInstrumentRow> search(String text, String type, int limit) {
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        String escaped = upper.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return searchMatching(upper, escaped + "%", "%" + escaped + "%", type, limit);
    }

    /**
     * The named instruments, delisted ones included, so a holding in one can
     * still be labelled. Symbols nobody knows are simply absent.
     */
    @Select("<script>" + LISTING + """
            WHERE COALESCE(e.ticker, m.scheme_code) IN
              <foreach item="symbol" collection="symbols" open="(" separator="," close=")">#{symbol}</foreach>
            ORDER BY symbol
            </script>""")
    List<TradableInstrumentRow> findBySymbols(@Param("symbols") List<String> symbols);
}

