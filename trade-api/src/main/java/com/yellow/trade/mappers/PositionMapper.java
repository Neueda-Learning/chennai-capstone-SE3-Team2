// contract: positions with quantity 0 are not returned -- filtering that is
// the service's job (below), not this query's job
//public interface PositionMapper {
//    List<PositionRow> findByAccountId(Long accountId);
//}

package com.yellow.trade.mappers;

import com.yellow.entities.Position; // adjust to your actual Sprint 5 domain class
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface PositionMapper {

    @Select("SELECT position_id, account_id, instrument_id, quantity, average_price " +
            "FROM position WHERE account_id = #{accountId} AND quantity > 0")
    List<Position> selectByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT position_id, account_id, instrument_id, quantity, average_price " +
            "FROM position WHERE account_id = #{accountId} AND instrument_id = #{instrumentId}")
    Position selectByAccountAndInstrument(@Param("accountId") Long accountId,
                                          @Param("instrumentId") Long instrumentId);

    @Insert("INSERT INTO position (account_id, instrument_id, quantity, average_price) " +
            "VALUES (#{accountId}, #{instrumentId}, #{quantity}, #{averagePrice})")
    int insertPosition(@Param("accountId") Long accountId,
                       @Param("instrumentId") Long instrumentId,
                       @Param("quantity") Integer quantity,
                       @Param("averagePrice") BigDecimal averagePrice);

    @Update("UPDATE position " +
            "SET quantity = #{quantity}, average_price = #{averagePrice} " +
            "WHERE account_id = #{accountId} AND instrument_id = #{instrumentId}")
    int updatePosition(@Param("accountId") Long accountId,
                       @Param("instrumentId") Long instrumentId,
                       @Param("quantity") Integer quantity,
                       @Param("averagePrice") BigDecimal averagePrice);
}