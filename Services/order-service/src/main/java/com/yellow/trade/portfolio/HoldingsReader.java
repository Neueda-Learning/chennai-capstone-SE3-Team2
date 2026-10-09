package com.yellow.trade.portfolio;

import com.yellow.trade.mappers.PositionMapper;
import com.yellow.trade.mappers.PositionRow;
import com.yellow.trade.portfolio.api.Holdings;
import org.springframework.stereotype.Component;

import java.util.List;

/** The portfolio module's answer to "what does this account hold", from the platform's positions. */
@Component
class HoldingsReader implements Holdings {

    private final PositionMapper positions;

    HoldingsReader(PositionMapper positions) {
        this.positions = positions;
    }

    @Override
    public List<String> heldSymbols(long accountId) {
        return positions.findByAccountId(accountId).stream()
                .filter(row -> row.getQuantity() != null && row.getQuantity().signum() > 0)
                .map(PositionRow::getSymbol)
                .distinct()
                .sorted()
                .toList();
    }
}
