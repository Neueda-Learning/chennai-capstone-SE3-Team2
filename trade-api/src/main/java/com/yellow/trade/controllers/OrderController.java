package com.yellow.trade.controllers;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.trade.dto.OrderResponse;
import com.yellow.trade.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
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
     * 201 with a Location header: this creates a resource, and the resource is
     * addressable at the DELETE below.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse placed = orderService.placeOrder(request);
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + placed.orderId()))
                .body(placed);
    }

    /**
     * 200 with the cancelled order rather than 204: the client needs the
     * resulting status to update the screen without a second request.
     */
    @DeleteMapping("/{orderId}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.OK)
    public OrderResponse cancelOrder(@PathVariable UUID orderId) {
        return orderService.cancelOrder(orderId);
    }
}
