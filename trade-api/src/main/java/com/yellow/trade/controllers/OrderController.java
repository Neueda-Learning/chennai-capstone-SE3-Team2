package com.yellow.trade.controllers;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.trade.dto.OrderResponse;
import com.yellow.trade.services.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Orders", description = "Order placement and management")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    //200, not 201.
    @Operation(summary = "Place a new order", description = "Submit an order to buy or sell an instrument. Order is validated and stored with status NEW. Execution happens asynchronously via Kafka.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order placed successfully", content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Order rejected: invalid data or insufficient cash for BUY (ORD-400)"),
        @ApiResponse(responseCode = "403", description = "Account not ACTIVE (ACC-403)"),
        @ApiResponse(responseCode = "404", description = "Account or instrument not found (ACC-404, INS-404)"),
        @ApiResponse(responseCode = "409", description = "Order conflict: duplicate idempotency key or insufficient shares for SELL (ORD-409)"),
        @ApiResponse(responseCode = "422", description = "Validation error: invalid quantity, price, or other constraints (VAL-422)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping
    public OrderResponse placeOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Order placement request",
                required = true,
                content = @Content(schema = @Schema(implementation = PlaceOrderRequest.class))
            )
            @Valid @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(request);
    }

    @Operation(summary = "Cancel an order", description = "Cancel a pending order. Only orders with status NEW can be cancelled.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order cancelled successfully", content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "403", description = "Account access denied (ACC-403)"),
        @ApiResponse(responseCode = "404", description = "Order not found (ORD-404)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/{id}")
    public OrderResponse cancelOrder(
            @Parameter(description = "Order ID (UUID)", required = true)
            @PathVariable("id") UUID id) {
        return orderService.cancelOrder(id);
    }
}
