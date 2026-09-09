package com.yellow.repositories;


import com.yellow.entities.Position;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class InMemoryPositionRepository implements PositionRepository {

    private record Key(Long accountId, Long instrumentId) {
        Key {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(instrumentId, "instrumentId");
        }
    }

    private final Map<Key, Position> byKey = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Optional<Position> find(Long accountId, Long instrumentId) {
        if (accountId == null || instrumentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(new Key(accountId, instrumentId)));
    }


    public Position save(Position position) {
        Position stored = position;
        if (position.positionId() == null) {
            stored = new Position(
                    nextId.getAndIncrement(),
                    position.accountId(),
                    position.instrumentId(),
                    position.quantity(),
                    position.averagePrice());
        }
        byKey.put(new Key(stored.accountId(), stored.instrumentId()), stored);
        return stored;
    }

    public void saveAll(Position... positions) {
        for (Position position : positions) {
            save(position);
        }
    }

    public void delete(Long accountId, Long instrumentId) {
        byKey.remove(new Key(accountId, instrumentId));
    }

    public List<Position> findByAccount(Long accountId) {
        return byKey.values().stream()
                .filter(p -> p.accountId().equals(accountId))
                .collect(Collectors.toList());
    }

    public int count() {
        return byKey.size();
    }

    public void clear() {
        byKey.clear();
        nextId.set(1);
    }
}