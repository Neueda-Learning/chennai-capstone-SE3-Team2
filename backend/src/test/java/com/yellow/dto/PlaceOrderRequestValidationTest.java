package com.yellow.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import com.yellow.enums.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
    @DisplayName("Should pass validation when request is valid")
    void shouldPassValidationWhenRequestIsValid() {
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(validRequest());
        assertThat(violations, is(empty()));
    }

    @Test
    @DisplayName("Should fail validation when account ID is null")
    void shouldFailValidationWhenAccountIdIsNull() {
        PlaceOrderRequest req = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.00"), "idemp-key-123");
        assertThat(validator.validate(req), is(not(empty())));
    }

    @Test
    @DisplayName("Should fail validation when symbol is blank")
    void shouldFailValidationWhenSymbolIsBlank() {
        PlaceOrderRequest req = new PlaceOrderRequest(1L, "   ", OrderSide.BUY, 10, new BigDecimal("150.00"), "idemp-key-123");
        assertThat(validator.validate(req), is(not(empty())));
    }

    @Test
    @DisplayName("Should fail validation when quantity is zero or negative")
    void shouldFailValidationWhenQuantityIsZeroOrNegative() {
        PlaceOrderRequest zeroReq = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 0, new BigDecimal("150.00"), "idemp-key-123");
        PlaceOrderRequest negReq = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, -1, new BigDecimal("150.00"), "idemp-key-123");

        assertThat(validator.validate(zeroReq), is(not(empty())));
        assertThat(validator.validate(negReq), is(not(empty())));
    }

    @Test
    @DisplayName("Should fail validation when price is zero or negative")
    void shouldFailValidationWhenPriceIsZeroOrNegative() {
        PlaceOrderRequest zeroReq = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("0.00"), "idemp-key-123");
        PlaceOrderRequest negReq = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("-0.01"), "idemp-key-123");

        assertThat(validator.validate(zeroReq), is(not(empty())));
        assertThat(validator.validate(negReq), is(not(empty())));
    }

    @Test
    @DisplayName("Should fail validation when price has more than two decimal places")
    void shouldFailValidationWhenPriceHasMoreThanTwoDecimalPlaces() {
        PlaceOrderRequest req = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.001"), "idemp-key-123");
        assertThat(validator.validate(req), is(not(empty())));
    }

    @Test
    @DisplayName("Should fail validation when idempotency key is shorter than eight characters")
    void shouldFailValidationWhenIdempotencyKeyIsShorterThanEightChars() {
        PlaceOrderRequest req = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.00"), "1234567");
        assertThat(validator.validate(req), is(not(empty())));
    }

    @Test
    @DisplayName("Should fail validation when idempotency key exceeds hundred characters")
    void shouldFailValidationWhenIdempotencyKeyExceedsHundredChars() {
        String longKey = "a".repeat(101);
        PlaceOrderRequest req = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.00"), longKey);
        assertThat(validator.validate(req), is(not(empty())));
    }

    @Test
    @DisplayName("Should pass validation when idempotency key is exactly eight characters")
    void shouldPassValidationWhenIdempotencyKeyIsExactlyEightChars() {
        PlaceOrderRequest req = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.00"), "12345678");
        assertThat(validator.validate(req), is(empty()));
    }

    @Test
    @DisplayName("Should pass validation when idempotency key is exactly hundred characters")
    void shouldPassValidationWhenIdempotencyKeyIsExactlyHundredChars() {
        String key = "a".repeat(100);
        PlaceOrderRequest req = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.00"), key);
        assertThat(validator.validate(req), is(empty()));
    }

    @Test
    @DisplayName("Should expose every field supplied through the constructor")
    void shouldExposeEveryFieldSuppliedThroughConstructor() {
        PlaceOrderRequest request = validRequest();

        assertThat(request.getAccountId(), is(equalTo(1L)));
        assertThat(request.getSymbol(), is(equalTo("AAPL")));
        assertThat(request.getSide(), is(equalTo(OrderSide.BUY)));
        assertThat(request.getQuantity(), is(equalTo(10)));
        assertThat(request.getPrice(), is(equalTo(new BigDecimal("150.00"))));
        assertThat(request.getIdempotencyKey(), is(equalTo("idemp-key-123")));
    }

    @Test
    @DisplayName("Should allow every field to be replaced through its setter")
    void shouldAllowEveryFieldToBeReplacedThroughItsSetter() {
        PlaceOrderRequest request = validRequest();

        request.setAccountId(2L);
        request.setSymbol("MSFT");
        request.setSide(OrderSide.SELL);
        request.setQuantity(5);
        request.setPrice(new BigDecimal("50.00"));
        request.setIdempotencyKey("idemp-key-456");

        assertThat(request.getAccountId(), is(equalTo(2L)));
        assertThat(request.getSymbol(), is(equalTo("MSFT")));
        assertThat(request.getSide(), is(equalTo(OrderSide.SELL)));
        assertThat(request.getQuantity(), is(equalTo(5)));
        assertThat(request.getPrice(), is(equalTo(new BigDecimal("50.00"))));
        assertThat(request.getIdempotencyKey(), is(equalTo("idemp-key-456")));
    }
}