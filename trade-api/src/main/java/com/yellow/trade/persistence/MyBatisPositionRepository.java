package com.yellow.trade.persistence;

import com.yellow.entities.Position;
import com.yellow.repositories.PositionRepository;
import com.yellow.trade.PlatformConstants;
import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.mappers.PositionRow;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MyBatisPositionRepository implements PositionRepository {

    private final PositionMapper positionMapper;

    public MyBatisPositionRepository(PositionMapper positionMapper) {
        this.positionMapper = positionMapper;
    }

    @Override
    public Optional<Position> find(Long accountId, Long instrumentId) {
        if (accountId == null || instrumentId == null) {
            return Optional.empty();
        }
        PositionRow row = positionMapper.findOne(
                accountId, instrumentId, PlatformConstants.DEFAULT_POSITION_TYPE);
        return Optional.ofNullable(row).map(RowMapping::toPosition);
    }
    @Override
    public Position save(Position position) {
        throw new UnsupportedOperationException(
                "positions move on fill, which arrives with the Trade Executor in Sprint 7");
    }
}
