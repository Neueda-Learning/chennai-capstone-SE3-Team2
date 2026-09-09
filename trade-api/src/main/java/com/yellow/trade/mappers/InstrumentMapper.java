//
//
//public interface InstrumentMapper {
//    String findSymbolById(Long instrumentId);
//}

package com.yellow.trade.mappers;

import com.yellow.entities.Instrument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InstrumentMapper {

    @Select("SELECT instrument_id, ticker, isin, trading_status " +
            "FROM instrument WHERE ticker = #{symbol}")
    Instrument selectBySymbol(@Param("symbol") String symbol);

    @Select("SELECT instrument_id, ticker, isin, trading_status " +
            "FROM instrument WHERE instrument_id = #{instrumentId}")
    Instrument findSymbolById(@Param("instrumentId") Long instrumentId);
}