package com.yellow.trade.watchlists;

import com.yellow.enums.Reason;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.security.AccountAccess;
import com.yellow.trade.watchlists.WatchExceptions.FundAlertException;
import com.yellow.trade.watchlists.WatchExceptions.LimitReachedException;
import com.yellow.trade.watchlists.WatchExceptions.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * A customer's price alerts: set, cancelled, re-armed. Firing is
 * QuoteEvaluator's, on the market-data stream. Every call is the caller's own
 * account only; the active-alert cap is decided under the account's lock.
 */
@Service
public class AlertService {

    private final AlertMapper alerts;
    private final WatchlistMapper watchlists;
    private final InstrumentMapper instruments;
    private final AccountAccess access;
    private final Clock clock;

    public AlertService(AlertMapper alerts, WatchlistMapper watchlists, InstrumentMapper instruments,
                        AccountAccess access, Clock clock) {
        this.alerts = alerts;
        this.watchlists = watchlists;
        this.instruments = instruments;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PriceAlert> list(long accountId) {
        access.requireOwn(accountId);
        return alerts.findForClient(accountId).stream().map(AlertRow::toAlert).toList();
    }

    /**
     * Only on what the poller prices: a tradable stock. A fund has no quote
     * on market-data, and an instrument not trading is never polled, so an
     * alert on either could never fire.
     */
    @Transactional
    public PriceAlert create(long accountId, AlertRequest request) {
        access.requireOwn(accountId);
        InstrumentRow instrument = instruments.findBySymbol(request.symbol());
        if (instrument == null) {
            throw new InstrumentNotFoundException(request.symbol(), Reason.UNKNOWN);
        }
        if ("MF".equals(instrument.getInstrumentType())) {
            throw new FundAlertException();
        }
        if (!instrument.isTradable()) {
            throw new InstrumentNotFoundException(request.symbol(), Reason.NOT_TRADABLE);
        }
        watchlists.lockAccount(accountId);
        requireRoomForAnother(accountId);

        AlertRow row = new AlertRow();
        row.setClientId(accountId);
        row.setInstrumentId(instrument.getInstrumentId());
        row.setDirection(request.direction().name());
        row.setThreshold(request.threshold());
        row.setCreatedAt(clock.instant());
        alerts.insert(row);
        return owned(accountId, row.getAlertId()).toAlert();
    }

    /** It stays in the list as CANCELLED; cancelling one already cancelled changes nothing. */
    @Transactional
    public void cancel(long accountId, long alertId) {
        access.requireOwn(accountId);
        if (alerts.cancel(accountId, alertId) == 0) {
            throw new NotFoundException("Alert");
        }
    }

    /** ACTIVE again, inside the cap; one already ACTIVE is answered as it is. */
    @Transactional
    public PriceAlert rearm(long accountId, long alertId) {
        access.requireOwn(accountId);
        AlertRow alert = owned(accountId, alertId);
        if (AlertStatus.ACTIVE.name().equals(alert.getStatus())) {
            return alert.toAlert();
        }
        watchlists.lockAccount(accountId);
        requireRoomForAnother(accountId);
        alerts.rearm(accountId, alertId);
        return owned(accountId, alertId).toAlert();
    }

    private void requireRoomForAnother(long accountId) {
        if (alerts.countActive(accountId) >= WatchLimits.ACTIVE_ALERTS) {
            throw new LimitReachedException("At most " + WatchLimits.ACTIVE_ALERTS + " active alerts an account");
        }
    }

    private AlertRow owned(long accountId, long alertId) {
        AlertRow row = alerts.findOwned(accountId, alertId);
        if (row == null) {
            throw new NotFoundException("Alert");
        }
        return row;
    }
}
