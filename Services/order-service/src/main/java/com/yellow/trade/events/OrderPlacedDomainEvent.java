package com.yellow.trade.events;

import com.yellow.entities.Order;
import org.springframework.context.ApplicationEvent;

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
