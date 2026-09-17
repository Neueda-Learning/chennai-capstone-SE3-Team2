package com.yellow.trade.events;

import com.yellow.entities.Order;
import org.springframework.context.ApplicationEvent;

/**
 * Spring ApplicationEvent published after an order is successfully placed and committed to the database.
 * This event is intended to be handled by a @TransactionalEventListener with phase=AFTER_COMMIT,
 * ensuring that event publishing only occurs after the transaction has successfully committed.
 */
public class OrderPlacedDomainEvent extends ApplicationEvent {

    private final Order order;

    public OrderPlacedDomainEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }
}
