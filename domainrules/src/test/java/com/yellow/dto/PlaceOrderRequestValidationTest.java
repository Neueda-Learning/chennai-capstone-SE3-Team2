package com.yellow.dto;

import com.yellow.enums.OrderSide;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.is;

class PlaceOrderRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private PlaceOrderRequest validRequest() {
        return new PlaceOrderRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                10,
                new BigDecimal("150.00"),
                "idemp-key-123"
        );
    }

    @Test
    @DisplayName("Should pass validation when request is valid")
    void shouldPassValidationWhenRequestIsValid() {
        assertThat(validator.validate(validRequest()), is(empty()));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    @DisplayName("Should fail validation when request contains an invalid field")
    void shouldFailValidationForInvalidRequest(Consumer<PlaceOrderRequest> modifier) {

        PlaceOrderRequest request = validRequest();
        modifier.accept(request);

        assertThat(validator.validate(request), is(not(empty())));
    }

    private static Stream<Consumer<PlaceOrderRequest>> invalidRequests() {
        return Stream.of(
                request -> request.setAccountId(null),
                request -> request.setSymbol("   "),
                request -> request.setQuantity(0),
                request -> request.setQuantity(-1),
                request -> request.setPrice(BigDecimal.ZERO),
                request -> request.setPrice(new BigDecimal("-0.01")),
                request -> request.setPrice(new BigDecimal("150.001")),
                request -> request.setIdempotencyKey("1234567"),
                request -> request.setIdempotencyKey("a".repeat(101))
        );
    }

    @Test
    @DisplayName("Should pass validation when idempotency key is exactly eight characters")
    void shouldPassValidationWhenIdempotencyKeyIsExactlyEightChars() {
        PlaceOrderRequest request = validRequest();
        request.setIdempotencyKey("12345678");

        assertThat(validator.validate(request), is(empty()));
    }

    @Test
    @DisplayName("Should pass validation when idempotency key is exactly hundred characters")
    void shouldPassValidationWhenIdempotencyKeyIsExactlyHundredChars() {
        PlaceOrderRequest request = validRequest();
        request.setIdempotencyKey("a".repeat(100));

        assertThat(validator.validate(request), is(empty()));
    }
}
