package com.yellow.repositories;

import com.yellow.entities.Instrument;
import java.util.Optional;

public interface InstrumentRepository {
    Optional<Instrument> findBySymbol(String symbol);
}
