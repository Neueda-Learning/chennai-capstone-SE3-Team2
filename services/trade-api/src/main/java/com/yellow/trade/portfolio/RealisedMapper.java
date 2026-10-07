package com.yellow.trade.portfolio;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** pf_realised: this module's one table, and only this module's. */
@Mapper
public interface RealisedMapper {

    /** Books one sale. Its event, or its order, already booked: 0 rows, a no-op. */
    @Insert("""
            INSERT INTO pf_realised
                   (event_id, order_id, client_id, instrument_id, symbol, quantity, sale_price, average_cost,
                    realised, booked_at)
            VALUES (#{eventId}, #{orderId}, #{clientId}, #{instrumentId}, #{symbol}, #{quantity}, #{salePrice},
                    #{averageCost}, #{realised}, #{bookedAt})
            ON CONFLICT DO NOTHING
            """)
    int insert(RealisedRow row);

    /** Booked in [from, until): the sum, zero when nothing was. */
    @Select("""
            SELECT COALESCE(sum(realised), 0)
            FROM pf_realised
            WHERE client_id = #{clientId} AND booked_at >= #{from} AND booked_at < #{until}
            """)
    BigDecimal total(@Param("clientId") long clientId, @Param("from") Instant from, @Param("until") Instant until);

    @Select("""
            SELECT symbol, sum(realised) AS realised
            FROM pf_realised
            WHERE client_id = #{clientId} AND booked_at >= #{from} AND booked_at < #{until}
            GROUP BY symbol
            ORDER BY symbol
            """)
    List<SymbolRealised> bySymbol(@Param("clientId") long clientId, @Param("from") Instant from,
                                  @Param("until") Instant until);
}
