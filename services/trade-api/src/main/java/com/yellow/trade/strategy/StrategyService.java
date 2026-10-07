package com.yellow.trade.strategy;

import com.yellow.enums.Reason;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.security.AccountAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * A customer's strategies: created, switched on and off, deleted, and their
 * runs read. Every call the caller's own account only, checked first
 * (ACC-403); a strategy is looked up on that account, so another account's
 * is STR-404. Firing is StrategyFirer's, on the market-data stream.
 */
@Service
public class StrategyService {

    static final int MAX_STRATEGIES = 10;

    private final StrategyMapper strategies;
    private final InstrumentMapper instruments;
    private final AccountAccess access;
    private final Clock clock;

    public StrategyService(StrategyMapper strategies, InstrumentMapper instruments, AccountAccess access, Clock clock) {
        this.strategies = strategies;
        this.instruments = instruments;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Strategy> list(long accountId) {
        access.requireOwn(accountId);
        return strategies.findForClient(accountId).stream().map(StrategyRow::toStrategy).toList();
    }

    /** Created disabled: nothing fires until the customer switches it on. Only on a tradable stock. */
    @Transactional
    public Strategy create(long accountId, StrategyRequest request) {
        access.requireOwn(accountId);
        InstrumentRow instrument = instruments.findBySymbol(request.symbol());
        if (instrument == null) {
            throw new InstrumentNotFoundException(request.symbol(), Reason.UNKNOWN);
        }
        if ("MF".equals(instrument.getInstrumentType())) {
            throw new StrategyExceptions.FundStrategyException();
        }
        if (!instrument.isTradable()) {
            throw new InstrumentNotFoundException(request.symbol(), Reason.NOT_TRADABLE);
        }
        strategies.lockAccount(accountId);
        if (strategies.countForClient(accountId) >= MAX_STRATEGIES) {
            throw new StrategyExceptions.LimitReachedException();
        }
        StrategyRow row = new StrategyRow();
        row.setClientId(accountId);
        row.setInstrumentId(instrument.getInstrumentId());
        row.setSide(request.side().name());
        row.setQuantity(request.quantity());
        row.setTriggerKind(request.trigger().name());
        row.setTriggerPrice(request.triggerPrice());
        row.setMaxSpend(request.maxSpend());
        row.setMaxPosition(request.maxPosition());
        row.setCreatedAt(clock.instant());
        strategies.insert(row);
        return owned(accountId, row.getStrategyId()).toStrategy();
    }

    /** Off at once (it waits for a firing under way, and the next sees it off); on re-arms it. */
    @Transactional
    public Strategy setEnabled(long accountId, long strategyId, boolean enabled) {
        access.requireOwn(accountId);
        owned(accountId, strategyId);
        if (enabled) {
            strategies.enable(accountId, strategyId);
        } else {
            strategies.disable(accountId, strategyId);
        }
        return owned(accountId, strategyId).toStrategy();
    }

    @Transactional
    public void delete(long accountId, long strategyId) {
        access.requireOwn(accountId);
        if (strategies.delete(accountId, strategyId) == 0) {
            throw new StrategyExceptions.NotFoundException();
        }
    }

    @Transactional(readOnly = true)
    public List<StrategyRun> runs(long accountId, long strategyId) {
        access.requireOwn(accountId);
        owned(accountId, strategyId);
        return strategies.findRuns(strategyId).stream().map(RunRow::toRun).toList();
    }

    private StrategyRow owned(long accountId, long strategyId) {
        StrategyRow row = strategies.findOwned(accountId, strategyId);
        if (row == null) {
            throw new StrategyExceptions.NotFoundException();
        }
        return row;
    }
}
