package com.yellow.dto;

import com.yellow.enums.OrderSide;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;


public class PlaceOrderRequest {

    @NotNull
    @Min(1)
    private Long accountId;


    @NotBlank
    @Size(min = 1, max = 20)
    private String symbol;


    @NotNull
    private OrderSide side;


    @NotNull
    @Min(1)
    private Integer quantity;


    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;


    @NotBlank
    @Size(min = 8, max = 100)
    private String idempotencyKey;

    public PlaceOrderRequest(
            Long accountId,
            String symbol,
            OrderSide side,
            Integer quantity,
            BigDecimal price,
            String idempotencyKey
    ) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getAccountId() {
        return accountId;
    }


    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }


    public String getSymbol() {
        return symbol;
    }


    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }


    public OrderSide getSide() {
        return side;
    }


    public void setSide(OrderSide side) {
        this.side = side;
    }


    public Integer getQuantity() {
        return quantity;
    }


    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }


    public BigDecimal getPrice() {
        return price;
    }


    public void setPrice(BigDecimal price) {
        this.price = price;
    }


    public String getIdempotencyKey() {
        return idempotencyKey;
    }


    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}