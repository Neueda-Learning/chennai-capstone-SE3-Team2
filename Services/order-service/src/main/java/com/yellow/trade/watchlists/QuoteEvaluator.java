package com.yellow.trade.watchlists;

import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.mappers.InstrumentRow;
import com.yellow.trade.notifications.api.AlertDelivery;
import com.yellow.trade.notifications.api.AlertNotice;
import com.yellow.trade.notifications.api.DeliveryReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * What one quote does, in one transaction: it becomes the instrument's latest
 * price, and every ACTIVE alert it crosses fires (decision log 0007).
 *
 * Firing is handing the alert to the notifications module's AlertDelivery and
 * marking it TRIGGERED with the notification that queued, together (decision
 * log 0008): an alert never reads TRIGGERED with nothing queued. This module
 * never resolves a channel; notifications does. If delivery fails, the whole
 * quote rolls back, the alert is still ACTIVE, and the next quote fires it.
 */
@Service
public class QuoteEvaluator {

    private static final Logger log = LoggerFactory.getLogger(QuoteEvaluator.class);

    private final InstrumentMapper instruments;
    private final LatestQuoteMapper latest;
    private final AlertMapper alerts;
    private final AlertDelivery delivery;
    private final Clock clock;

    public QuoteEvaluator(InstrumentMapper instruments, LatestQuoteMapper latest, AlertMapper alerts,
                          AlertDelivery delivery, Clock clock) {
        this.instruments = instruments;
        this.latest = latest;
        this.alerts = alerts;
        this.delivery = delivery;
        this.clock = clock;
    }

    @Transactional
    public void evaluate(MarketQuote quote) {
        InstrumentRow instrument = instruments.findBySymbol(quote.symbol());
        if (instrument == null) {
            log.debug("quote for {}, which the platform does not list: passed by", quote.symbol());
            return;
        }
        Instant now = clock.instant();
        long instrumentId = instrument.getInstrumentId();
        if (latest.hold(instrumentId, quote.price(), quote.changePercent(), quote.stale(), quote.quoteAsOf(),
                quote.eventId(), now) == 0) {
            log.debug("quote {} for {} observed before the one held: nothing changes", quote.eventId(), quote.symbol());
            return;
        }
        for (AlertRow alert : alerts.lockCrossed(instrumentId, quote.price())) {
            DeliveryReceipt receipt = delivery.deliver(new AlertNotice(quote.eventId(), alert.getClientId(),
                    alert.getAlertId(), quote.symbol(), AlertNotice.Direction.valueOf(alert.getDirection()),
                    alert.getThreshold(), quote.price(), quote.quoteAsOf()));
            alerts.markTriggered(alert.getAlertId(), quote.price(), now, receipt.notificationId());
            log.info("alert {} on {} TRIGGERED at {} for account {}: notification {}", alert.getAlertId(),
                    quote.symbol(), quote.price(), alert.getClientId(), receipt.notificationId());
        }
    }
}
