package com.yellow.repositories;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.yellow.entities.Instrument;

public class InMemoryInstrumentRepository implements InstrumentRepository {

    private final Map<String, Instrument> bySymbol = new ConcurrentHashMap<>();
    private final Map<Long, Instrument> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<Instrument> findBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySymbol.get(symbol));
    }


    public Instrument save(Instrument instrument) {
        Instrument existing = bySymbol.get(instrument.symbol());
        if (existing != null
                && !existing.instrumentId().equals(instrument.instrumentId())) {
            throw new IllegalStateException(
                    "symbol " + instrument.symbol() + " is already held by "
                            + "instrument " + existing.instrumentId());
        }
        bySymbol.put(instrument.symbol(), instrument);
        byId.put(instrument.instrumentId(), instrument);
        return instrument;
    }

    public void saveAll(Instrument... instruments) {
        for (Instrument instrument : instruments) {
            save(instrument);
        }
    }

    public Optional<Instrument> findById(Long instrumentId) {
        if (instrumentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(instrumentId));
    }

    public int count() {
        return byId.size();
    }

    public void clear() {
        bySymbol.clear();
        byId.clear();
    }
}