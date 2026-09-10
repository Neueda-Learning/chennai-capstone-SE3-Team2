package com.yellow.trade.mappers;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * position, joined to each holding's instrument symbol.
 *
 * Read-only this sprint. A holding changes when an order FILLS, and filling
 * is the Trade Executor's job in Sprint 7 -- placing an order blocks cash and
 * records the order, and moves no stock. Write statements for a fill belong
 * with the code that fills, not here as dead methods nothing calls.
 */
@Mapper
public interface PositionMapper {

    String SELECT_COLUMNS = """
            SELECT p.position_id,
                   p.client_id,
                   p.instrument_id,
                   COALESCE(e.ticker, m.scheme_code) AS symbol,
                   p.position_type,
                   p.quantity,
                   p.average_price
            FROM position p
            JOIN instrument   i ON i.instrument_id = p.instrument_id
            LEFT JOIN equity      e ON e.instrument_id = p.instrument_id
            LEFT JOIN mutual_fund m ON m.instrument_id = p.instrument_id
            """;

    /**
     * No quantity filter: ck_position_quantity_positive already guarantees
     * every row is a real holding, and a position is deleted when it closes
     * rather than zeroed. Filtering here would imply the schema allows a state
     * it does not.
     *
     * Ordered so the response is stable between calls -- an unordered read of
     * the same rows in a different order looks like a change to a client
     * diffing them.
     */
    @Select(SELECT_COLUMNS + " WHERE p.client_id = #{accountId} ORDER BY symbol, p.position_type")
    List<PositionRow> findByAccountId(@Param("accountId") Long accountId);

    /**
     * The natural key is (client_id, instrument_id, position_type), so all
     * three are needed to name one row. Rule 7 asks about a specific holding.
     */
    @Select(SELECT_COLUMNS + """
             WHERE p.client_id     = #{accountId}
               AND p.instrument_id = #{instrumentId}
               AND p.position_type = #{positionType}
            """)
    PositionRow findOne(@Param("accountId") Long accountId,
                        @Param("instrumentId") Long instrumentId,
                        @Param("positionType") String positionType);

    /**
     * Opens a holding that did not exist. Returns the affected row count, so
     * the caller can tell an insert that happened from one that did not.
     */
    @Insert("""
            INSERT INTO position (client_id, instrument_id, position_type, quantity, average_price)
            VALUES (#{accountId}, #{instrumentId}, #{positionType}, #{quantity}, #{averagePrice})
            """)
    int insertPosition(@Param("accountId") Long accountId,
                       @Param("instrumentId") Long instrumentId,
                       @Param("positionType") String positionType,
                       @Param("quantity") BigDecimal quantity,
                       @Param("averagePrice") BigDecimal averagePrice);

    /**
     * Moves an existing holding. The whole natural key is in the WHERE clause,
     * because two thirds of it identifies more than one row.
     */
    @Update("""
            UPDATE position
               SET quantity      = #{quantity},
                   average_price = #{averagePrice}
             WHERE client_id     = #{accountId}
               AND instrument_id = #{instrumentId}
               AND position_type = #{positionType}
            """)
    int updatePosition(@Param("accountId") Long accountId,
                       @Param("instrumentId") Long instrumentId,
                       @Param("positionType") String positionType,
                       @Param("quantity") BigDecimal quantity,
                       @Param("averagePrice") BigDecimal averagePrice);

    /** Removes a holding that has been sold in full. */
    @Delete("""
            DELETE FROM position
             WHERE client_id     = #{accountId}
               AND instrument_id = #{instrumentId}
               AND position_type = #{positionType}
            """)
    int deletePosition(@Param("accountId") Long accountId,
                       @Param("instrumentId") Long instrumentId,
                       @Param("positionType") String positionType);
}
