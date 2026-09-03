package com.yellow;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import com.yellow.enums.*;
import com.yellow.dto.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PlaceOrderRequestValidationTest {

    private static ValidatorFactory factory;
    private Validator validator;

    @BeforeAll
    static void beforeAll() {
        factory = Validation.buildDefaultValidatorFactory();
    }

    @BeforeEach
    void setUp() {
        validator = factory.getValidator();
    }

    private PlaceOrderRequest validRequest() {
        return new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.00"), "idemp-key-123");
    }

    @Test
    void shouldPassValidationWhenRequestIsValid() {
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(validRequest());
        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenAccountIdIsNull() {
        PlaceOrderRequest req = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.00"), "idemp-key-123");
        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void shouldFailValidationWhenSymbolIsBlank() {
        PlaceOrderRequest req = new PlaceOrderRequest(1L, "   ", OrderSide.BUY, 10, new BigDecimal("150.00"), "idemp-key-123");
        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void shouldFailValidationWhenQuantityIsZeroOrNegative() {
        PlaceOrderRequest zeroReq = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 0, new BigDecimal("150.00"), "idemp-key-123");
        PlaceOrderRequest negReq = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, -1, new BigDecimal("150.00"), "idemp-key-123");

        assertFalse(validator.validate(zeroReq).isEmpty());
        assertFalse(validator.validate(negReq).isEmpty());
    }

    @Test
    void shouldFailValidationWhenPriceIsZeroOrNegative() {
        PlaceOrderRequest zeroReq = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("0.00"), "idemp-key-123");
        PlaceOrderRequest negReq = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("-0.01"), "idemp-key-123");

        assertFalse(validator.validate(zeroReq).isEmpty());
        assertFalse(validator.validate(negReq).isEmpty());
    }

    @Test
    void shouldFailValidationWhenPriceHasMoreThanTwoDecimalPlaces() {
        PlaceOrderRequest req = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.001"), "idemp-key-123");
        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void shouldFailValidationWhenIdempotencyKeyIsShorterThanEightChars() {
        PlaceOrderRequest req = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.00"), "1234567");
        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void shouldFailValidationWhenIdempotencyKeyExceedsHundredChars() {
        String longKey = "a".repeat(101);
        PlaceOrderRequest req = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.00"), longKey);
        assertFalse(validator.validate(req).isEmpty());
    }
}