package com.yellow.trade.strategy;

import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Every quote on market-data, once: which enabled, armed strategies on its
 * instrument it reaches (through ix_strat_armed), and each one fired in its
 * own transaction, so one strategy's failure never holds up another's.
 */
@Service
public class StrategyTrigger {

    private static final Logger log = LoggerFactory.getLogger(StrategyTrigger.class);

    private final InstrumentMapper instruments;
    private final StrategyMapper strategies;
    private final StrategyFirer firer;

    public StrategyTrigger(InstrumentMapper instruments, StrategyMapper strategies, StrategyFirer firer) {
        this.instruments = instruments;
        this.strategies = strategies;
        this.firer = firer;
    }

    public void onQuote(StrategyQuote quote) {
        InstrumentRow instrument = instruments.findBySymbol(quote.symbol());
        if (instrument == null) {
            return;
        }
        List<Long> crossed = strategies.findCrossed(instrument.getInstrumentId(), quote.price());
        for (Long strategyId : crossed) {
            try {
                firer.fire(strategyId, quote);
            } catch (RuntimeException e) {
                // That strategy, this quote: its transaction rolled back, and the next quote tries again.
                log.warn("strategy {} not fired on quote {}: {}", strategyId, quote.eventId(), e.getClass().getSimpleName());
            }
        }
    }
}
