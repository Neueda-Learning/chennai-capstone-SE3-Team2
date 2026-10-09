package com.yellow.trade.persistence;

import com.yellow.entities.Instrument;
import com.yellow.repositories.InstrumentRepository;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisInstrumentRepository implements InstrumentRepository {

    private final InstrumentMapper instrumentMapper;

    public MyBatisInstrumentRepository(InstrumentMapper instrumentMapper) {
        this.instrumentMapper = instrumentMapper;
    }
    @Override
    public Optional<Instrument> findBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        InstrumentRow row = instrumentMapper.findBySymbol(symbol);
        return Optional.ofNullable(row).map(RowMapping::toInstrument);
    }
}
