package com.yellow.trade.mappers;

import java.util.List;

// contract: positions with quantity 0 are not returned -- filtering that is
// the service's job (below), not this query's job
public interface PositionMapper {
    List<PositionRow> findByAccountId(Long accountId);
}