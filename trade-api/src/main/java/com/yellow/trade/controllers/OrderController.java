package com.yellow.trade.controllers;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.trade.dto.OrderResponse;
import com.yellow.trade.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // PlaceOrderRequest is the domain's own DTO -- its fields already match
    // the contract's PlaceOrderRequest schema field for field, no separate
    // web-layer copy needed
    @PostMapping("/api/v1/orders")
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(request);
    }

    @DeleteMapping("/api/v1/orders/{id}")
    public OrderResponse cancelOrder(@PathVariable UUID id) {
        return orderService.cancelOrder(id);
    }
}