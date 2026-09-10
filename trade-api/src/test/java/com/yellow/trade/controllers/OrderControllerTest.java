package com.yellow.trade.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.exceptions.DuplicateOrderException;
import com.yellow.exceptions.InsufficientFundsException;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.enums.Reason;
import com.yellow.trade.dto.OrderResponse;
import com.yellow.trade.services.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The web layer alone: statuses, the envelope, and validation. No database and
 * no container -- the service is mocked, because what is under test here is the
 * translation between HTTP and a service call.
 */
@WebMvcTest(OrderController.class)
@org.springframework.context.annotation.Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    private static final String VALID_BODY = """
            {"accountId":3,"symbol":"APEX","side":"BUY","quantity":10,
             "price":1450.00,"idempotencyKey":"key-12345678"}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private OrderService orderService;

    private static OrderResponse placed() {
        return new OrderResponse(
                UUID.fromString("6f9619ff-8b86-d011-b42d-00c04fc964ff"), 3L, "APEX",
                OrderSide.BUY, new BigDecimal("10"), new BigDecimal("1450.00"),
                OrderStatus.NEW, "Order accepted", Instant.parse("2026-09-10T10:00:00Z"));
    }

    @Test
    @DisplayName("a placed order answers 201 with a Location header")
    void placedOrderIsCreated() throws Exception {
        when(orderService.placeOrder(any())).thenReturn(placed());

        mockMvc.perform(post("/api/v1/orders").contentType("application/json").content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/6f9619ff-8b86-d011-b42d-00c04fc964ff"))
                .andExpect(jsonPath("$.orderId").value("6f9619ff-8b86-d011-b42d-00c04fc964ff"))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @DisplayName("the order id is returned bare, with no display prefix")
    void orderIdHasNoPrefix() throws Exception {
        when(orderService.placeOrder(any())).thenReturn(placed());

        // The value handed out must be the value DELETE accepts back.
        mockMvc.perform(post("/api/v1/orders").contentType("application/json").content(VALID_BODY))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("ORD-6f9619ff"))));
    }

    @Test
    @DisplayName("insufficient funds is ORD-400 in the envelope")
    void insufficientFundsIsOrd400() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
                new InsufficientFundsException(new BigDecimal("14500"), new BigDecimal("100")));

        mockMvc.perform(post("/api/v1/orders").contentType("application/json").content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORD-400"))
                .andExpect(jsonPath("$.message").value("Insufficient funds"));
    }

    @Test
    @DisplayName("a reused idempotency key is ORD-409")
    void duplicateKeyIsOrd409() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(new DuplicateOrderException("key-12345678", null));

        mockMvc.perform(post("/api/v1/orders").contentType("application/json").content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORD-409"));
    }

    @Test
    @DisplayName("an unknown instrument is INS-404, and the body names no symbol")
    void unknownInstrumentIsIns404() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(
                new InstrumentNotFoundException("SECRETSYMBOL", Reason.UNKNOWN));

        mockMvc.perform(post("/api/v1/orders").contentType("application/json").content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INS-404"))
                // What was asked for is logged, never echoed.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SECRETSYMBOL"))));
    }

    @Test
    @DisplayName("a non-positive quantity is VAL-422 before the service is called")
    void nonPositiveQuantityIsVal422() throws Exception {
        String body = VALID_BODY.replace("\"quantity\":10", "\"quantity\":0");

        mockMvc.perform(post("/api/v1/orders").contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"))
                .andExpect(jsonPath("$.message").value("Invalid input"));
    }

    @Test
    @DisplayName("an idempotency key below the minimum length is VAL-422")
    void shortIdempotencyKeyIsVal422() throws Exception {
        String body = VALID_BODY.replace("key-12345678", "short");

        mockMvc.perform(post("/api/v1/orders").contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    @DisplayName("a malformed body leaves as the envelope, not as a whitelabel page")
    void malformedBodyStaysInTheEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType("application/json").content("{ not json"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    @DisplayName("an order id that is not a UUID is VAL-422, not a 500")
    void unparseableOrderIdIsVal422() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/not-a-uuid"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    @DisplayName("an unexpected failure still leaves as the envelope and leaks no internals")
    void unexpectedFailureStaysInTheEnvelope() throws Exception {
        when(orderService.placeOrder(any())).thenThrow(new IllegalStateException("connection pool exhausted"));

        mockMvc.perform(post("/api/v1/orders").contentType("application/json").content(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("SRV-500"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("connection pool"))));
    }
}
