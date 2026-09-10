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

    /**
     * Returns delisted instruments too. Rule 3 asks two questions -- does the
     * symbol exist, and may it be traded -- and the domain answers both, with
     * the same INS-404 but a different typed reason on the exception. Hiding
     * untradable rows here would collapse the two into one and lose the
     * distinction the server log needs.
     */
    @Override
    public Optional<Instrument> findBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        InstrumentRow row = instrumentMapper.findBySymbol(symbol);
        return Optional.ofNullable(row).map(RowMapping::toInstrument);
    }
}
