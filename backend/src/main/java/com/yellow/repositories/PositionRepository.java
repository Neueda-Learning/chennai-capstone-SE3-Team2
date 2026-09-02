package com.yellow.repositories;

import com.yellow.entities.Position;

import java.util.Optional;

public interface PositionRepository {
    Optional<Position> find(Long accountId, Long instrumentId);
    Position save(Position position);
}
