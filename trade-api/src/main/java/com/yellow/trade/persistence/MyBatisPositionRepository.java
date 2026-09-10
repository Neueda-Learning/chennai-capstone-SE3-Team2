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

    /**
     * The domain's key is (account, instrument); the schema's is (client,
     * instrument, position type). DELIVERY closes the gap, because every
     * position this service can create is delivery -- product_type defaults
     * to CNC. When Sprint 7 offers intraday, this is the method that has to
     * learn about the third part of the key, and the domain's Position needs
     * a position type before it can.
     */
    @Override
    public Optional<Position> find(Long accountId, Long instrumentId) {
        if (accountId == null || instrumentId == null) {
            return Optional.empty();
        }
        PositionRow row = positionMapper.findOne(
                accountId, instrumentId, PlatformConstants.DEFAULT_POSITION_TYPE);
        return Optional.ofNullable(row).map(RowMapping::toPosition);
    }

    /**
     * A holding moves when an order FILLS, and filling is the Trade Executor
     * in Sprint 7. Placing an order blocks cash and records the order; it
     * moves no stock, so nothing in this sprint calls this method.
     *
     * It throws rather than silently doing nothing: a caller that reaches
     * here has assumed a capability this sprint does not have, and a quiet
     * no-op would lose a holding without reporting it.
     */
    @Override
    public Position save(Position position) {
        throw new UnsupportedOperationException(
                "positions move on fill, which arrives with the Trade Executor in Sprint 7");
    }
}
