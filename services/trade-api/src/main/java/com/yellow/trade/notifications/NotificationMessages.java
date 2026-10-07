package com.yellow.trade.notifications;

import com.yellow.trade.notifications.api.AlertNotice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * What a customer reads. The order itself and nothing else: no account
 * number, no order id, no address, nothing contracts/kafka-topics.md keeps off
 * a message. Rupees, since the platform's universe is INR only (decision log
 * 0010), and times in IST.
 */
final class NotificationMessages {

    record Message(String subject, String body) {
    }

    private static final Locale INDIA = Locale.forLanguageTag("en-IN");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm 'IST on' d MMM yyyy", Locale.ENGLISH);

    private NotificationMessages() {
    }

    static Message forTrade(TradeEvent event) {
        String side = event.side().equals("BUY") ? "buy" : "sell";
        String what = quantity(event.quantity()) + " " + event.symbol();
        String nothing = event.side().equals("BUY") ? "Nothing was bought" : "Nothing was sold";

        return switch (event.kind()) {
            case ORDER_FILLED -> new Message(
                    (event.side().equals("BUY") ? "Bought " : "Sold ") + what + " at " + rupees(event.executedPrice()),
                    "Your order to " + side + " " + what + " was executed at " + rupees(event.executedPrice())
                            + " each, " + rupees(event.quantity().multiply(event.executedPrice())) + " in all.");
            case ORDER_REJECTED -> new Message(
                    "Order rejected: " + side + " " + what,
                    "Your order to " + side + " " + what + " was rejected: " + rejection(event.reason()) + ". "
                            + nothing + " and no money moved.");
            case ORDER_CANCELLED -> new Message(
                    "Order cancelled: " + side + " " + what,
                    "Your order to " + side + " " + what + " was cancelled"
                            + ("CANCELLED_BY_CUSTOMER".equals(event.reason()) ? ", as you asked," : "")
                            + " before it was carried out. " + nothing
                            + (event.side().equals("BUY") ? ", and the cash it held back is available again." : "."));
            case PRICE_ALERT -> throw new IllegalArgumentException("a price alert is not a trade event");
        };
    }

    static Message forAlert(AlertNotice notice) {
        String moved = notice.direction() == AlertNotice.Direction.ABOVE ? " rose to " : " fell to ";
        return new Message(
                notice.symbol() + moved + rupees(notice.price()),
                "Your alert for " + notice.symbol() + " at " + rupees(notice.threshold()) + " was crossed: the price was "
                        + rupees(notice.price()) + " at " + WHEN.format(notice.quoteAsOf().atZone(IST))
                        + ". The alert has fired and stays off until you re-arm it.");
    }

    /** The executor's reject reasons (RejectReason), in words; one added later still reads. */
    private static String rejection(String reason) {
        if (reason == null) {
            return "the platform could not carry it out";
        }
        return switch (reason) {
            case "INSUFFICIENT_FUNDS" -> "there was not enough cash available for it";
            case "INSUFFICIENT_HOLDINGS" -> "you do not hold that many to sell";
            case "PRICE_NOT_MET" -> "the market price did not reach your limit";
            case "INSTRUMENT_NOT_TRADABLE" -> "the instrument is not open for trading";
            case "INSTRUMENT_NOT_PRICEABLE", "NO_PRICE" -> "no price could be found for the instrument";
            case "ACCOUNT_NOT_ACTIVE" -> "your account is not active";
            default -> "the platform could not carry it out";
        };
    }

    private static String quantity(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }

    private static String rupees(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(INDIA);
        return format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }
}
