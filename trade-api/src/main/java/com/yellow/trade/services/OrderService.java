package com.yellow.trade.services;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.entities.Order;
import com.yellow.enums.OrderStatus;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.OrderNotFoundException;
import com.yellow.repositories.OrderRepository;
import com.yellow.trade.dto.OrderResponse;
import com.yellow.trade.mappers.InstrumentMapper;
import com.yellow.trade.security.TokenAccountContext;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderService {

    // the domain's own OrderService, not a mapper -- rules 1 to 8 already
    // live here from Sprint 5, we don't rewrite any of them
    private final com.yellow.services.OrderService domainOrderService;

    // Order only stores instrumentId, but the contract needs the symbol string
    private final InstrumentMapper instrumentMapper;

    // needed to learn WHICH account an order belongs to before cancelling --
    // the path only carries an order id, not an account id
    private final OrderRepository orderRepository;

    private final TokenAccountContext tokenAccountContext;

    public OrderService(com.yellow.services.OrderService domainOrderService,
                         InstrumentMapper instrumentMapper,
                         OrderRepository orderRepository,
                         TokenAccountContext tokenAccountContext) {
        this.domainOrderService = domainOrderService;
        this.instrumentMapper = instrumentMapper;
        this.orderRepository = orderRepository;
        this.tokenAccountContext = tokenAccountContext;
    }

    public OrderResponse placeOrder(PlaceOrderRequest request) {
        // account key is already known here, straight from the request body --
        // no lookup needed, check it before touching the domain at all
        if (!request.getAccountId().equals(tokenAccountContext.currentAccountId())) {
            // no real status to report -- this isn't "the account is suspended",
            // it's "this token was never for this account in the first place"
            throw new AccountNotActiveException(null);
        }
        Order order = domainOrderService.placeOrder(request);
        return toOrderResponse(order);
    }

    public OrderResponse cancelOrder(UUID orderId) {
        Order existing = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!existing.accountId().equals(tokenAccountContext.currentAccountId())) {
            throw new AccountNotActiveException(null);
        }

        Order cancelled = domainOrderService.cancelOrder(orderId);
        return toOrderResponse(cancelled);
    }

    private OrderResponse toOrderResponse(Order order) {
        return new OrderResponse(
                "ORD-" + order.orderId(), // contract: display prefix over the stored UUID
                order.status(),
                messageFor(order.status()),
                instrumentMapper.findSymbolById(order.instrumentId()),
                order.side(),
                order.quantity().intValueExact(),
                order.limitPrice()
        );
    }

    // message is display-only per the contract -- "never branch on this string"
    private String messageFor(OrderStatus status) {
        return switch (status) {
            case NEW -> "Order accepted";
            case FILLED -> "Order executed";
            case REJECTED -> "Order rejected";
            case CANCELLED -> "Order cancelled";
        };
    }
}