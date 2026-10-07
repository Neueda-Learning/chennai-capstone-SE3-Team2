package com.yellow.trade.notifications;

import com.yellow.trade.notifications.NotificationMessages.Message;
import com.yellow.trade.notifications.api.AlertNotice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class NotificationMessagesTest {

    private static final UUID EVENT = UUID.fromString("d47f9a10-3e2b-4c88-b0a1-7e6d5c4b3a29");
    private static final UUID ORDER = UUID.fromString("6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e");
    private static final Instant AT = Instant.parse("2026-10-06T03:44:24Z");

    private static TradeEvent event(NotificationKind kind, String side, String quantity, String executedPrice,
                                    String reason) {
        return new TradeEvent(EVENT, kind, AT, ORDER, 3L, "TATASTEEL.NS", side, new BigDecimal(quantity),
                new BigDecimal("180.00"), executedPrice == null ? null : new BigDecimal(executedPrice), reason);
    }

    @Test
    @DisplayName("a fill says what was bought, at what price, and what it came to")
    void fill() {
        Message message = NotificationMessages.forTrade(event(NotificationKind.ORDER_FILLED, "BUY", "2", "178.72", null));

        assertThat(message.subject(), is("Bought 2 TATASTEEL.NS at ₹178.72"));
        assertThat(message.body(), is("Your order to buy 2 TATASTEEL.NS was executed at ₹178.72 each, ₹357.44 in all."));
    }

    @Test
    @DisplayName("a sale, and a fund's fractional units, read the same way")
    void saleOfUnits() {
        Message message = NotificationMessages.forTrade(
                event(NotificationKind.ORDER_FILLED, "SELL", "12.3450", "1234.5", null));

        assertThat(message.subject(), is("Sold 12.345 TATASTEEL.NS at ₹1,234.50"));
        assertThat(message.body(), containsString("₹15,239.90 in all"));
    }

    @Test
    @DisplayName("a rejection is news: it says the order failed, why, in words, and that no money moved")
    void rejection() {
        Message message = NotificationMessages.forTrade(
                event(NotificationKind.ORDER_REJECTED, "BUY", "2", null, "INSUFFICIENT_FUNDS"));

        assertThat(message.subject(), is("Order rejected: buy 2 TATASTEEL.NS"));
        assertThat(message.body(), is("Your order to buy 2 TATASTEEL.NS was rejected: there was not enough cash "
                + "available for it. Nothing was bought and no money moved."));
    }

    @Test
    @DisplayName("every reason the executor gives has a sentence, and one it does not know still reads")
    void reasons() {
        for (String reason : new String[] {"INSUFFICIENT_HOLDINGS", "PRICE_NOT_MET", "INSTRUMENT_NOT_TRADABLE",
                "INSTRUMENT_NOT_PRICEABLE", "ACCOUNT_NOT_ACTIVE", "NO_PRICE", "SOMETHING_NEW", null}) {
            String body = NotificationMessages.forTrade(
                    event(NotificationKind.ORDER_REJECTED, "SELL", "1", null, reason)).body();
            assertThat(body, not(containsString("_")));
            assertThat(body, containsString("Nothing was sold"));
        }
    }

    @Test
    @DisplayName("a cancellation says the order will not happen, and that the cash it held is free again")
    void cancellation() {
        Message message = NotificationMessages.forTrade(
                event(NotificationKind.ORDER_CANCELLED, "BUY", "2", null, "CANCELLED_BY_CUSTOMER"));

        assertThat(message.subject(), is("Order cancelled: buy 2 TATASTEEL.NS"));
        assertThat(message.body(), is("Your order to buy 2 TATASTEEL.NS was cancelled, as you asked, before it was "
                + "carried out. Nothing was bought, and the cash it held back is available again."));
    }

    @Test
    @DisplayName("a crossed alert says which way, the level, the price and when that price was seen, in IST")
    void alert() {
        Message message = NotificationMessages.forAlert(new AlertNotice(EVENT, 3L, 7L, "TATASTEEL.NS",
                AlertNotice.Direction.ABOVE, new BigDecimal("180"), new BigDecimal("181.2"), AT));

        assertThat(message.subject(), is("TATASTEEL.NS rose to ₹181.20"));
        assertThat(message.body(), is("Your alert for TATASTEEL.NS at ₹180.00 was crossed: the price was ₹181.20 "
                + "at 09:14 IST on 6 Oct 2026. The alert has fired and stays off until you re-arm it."));
    }

    @Test
    @DisplayName("a fall reads as a fall")
    void alertBelow() {
        Message message = NotificationMessages.forAlert(new AlertNotice(EVENT, 3L, 7L, "ITC.NS",
                AlertNotice.Direction.BELOW, new BigDecimal("400"), new BigDecimal("399.95"), AT));

        assertThat(message.subject(), is("ITC.NS fell to ₹399.95"));
    }

    @Test
    @DisplayName("nothing in a message but the order itself: no account, no order id, no address")
    void nothingSensitive() {
        Message message = NotificationMessages.forTrade(event(NotificationKind.ORDER_FILLED, "BUY", "2", "178.72", null));
        String text = message.subject() + message.body();

        assertThat(text, not(containsString(ORDER.toString())));
        assertThat(text, not(containsString(EVENT.toString())));
        assertThat(text, not(containsString("account")));
    }
}
