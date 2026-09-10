package com.yellow.trade.controllers;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.trade.dto.OrderResponse;
import com.yellow.trade.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Placing and cancelling orders.
 *
 * PlaceOrderRequest is the domain's own DTO rather than a web-layer copy. Its
 * six fields and their constraints are business constraints, not transport
 * ones -- the Trade Executor replaying an order needs the same limits without
 * reimplementing them -- so it lives in the domain and is reused here.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 200, not 201. The contract fixes it, and the reason is visible in the
     * response: this endpoint does not merely create a pending resource, it
     * returns the order's outcome.
     */
    @PostMapping
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(request);
    }

    /**
     * The path takes the bare UUID, "without the ORD- display prefix" -- the
     * prefixed form is what responses carry. 200 with the cancelled order,
     * so the client can update the screen without a second request.
     */
    @DeleteMapping("/{id}")
    public OrderResponse cancelOrder(@PathVariable("id") UUID id) {
        return orderService.cancelOrder(id);
    }
}
