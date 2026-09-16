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

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    //200, not 201.
    @PostMapping
    public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(request);
    }

    @DeleteMapping("/{id}")
    public OrderResponse cancelOrder(@PathVariable("id") UUID id) {
        return orderService.cancelOrder(id);
    }
}
