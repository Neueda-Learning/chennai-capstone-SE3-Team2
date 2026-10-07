package com.yellow.trade.strategy;

import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Every quote on market-data, once: which enabled, armed strategies on its
 * instrument it reaches (through ix_strat_armed), and each one fired in its
 * own transaction, so one strategy's failure never holds up another's.
 * Indicator strategies are read the instrument's view once for all of them,
 * and only when there are some: a stock with none costs no candle read.
 */
@Service
public class StrategyTrigger {

    private static final Logger log = LoggerFactory.getLogger(StrategyTrigger.class);

    private final InstrumentMapper instruments;
    private final StrategyMapper strategies;
    private final StrategyFirer firer;
    private final IndicatorReader indicators;

    public StrategyTrigger(InstrumentMapper instruments, StrategyMapper strategies, StrategyFirer firer,
                           IndicatorReader indicators) {
        this.instruments = instruments;
        this.strategies = strategies;
        this.firer = firer;
        this.indicators = indicators;
    }

    public void onQuote(StrategyQuote quote) {
        InstrumentRow instrument = instruments.findBySymbol(quote.symbol());
        if (instrument == null) {
            return;
        }
        for (Long strategyId : strategies.findCrossed(instrument.getInstrumentId(), quote.price())) {
            fire(strategyId, quote, () -> firer.fire(strategyId, quote));
        }
        List<Long> indicatorStrategies = strategies.findArmedIndicators(instrument.getInstrumentId());
        if (indicatorStrategies.isEmpty()) {
            return;
        }
        Optional<Indicators.View> view = indicators.view(quote.symbol(), quote);
        if (view.isEmpty()) {
            return;
        }
        for (Long strategyId : indicatorStrategies) {
            fire(strategyId, quote, () -> firer.fire(strategyId, quote, view.get()));
        }
    }

    private static void fire(long strategyId, StrategyQuote quote, Runnable firing) {
        try {
            firing.run();
        } catch (RuntimeException e) {
            // That strategy, this quote: its transaction rolled back, and the next quote tries again.
            log.warn("strategy {} not fired on quote {}: {}", strategyId, quote.eventId(), e.getClass().getSimpleName());
        }
    }
}
