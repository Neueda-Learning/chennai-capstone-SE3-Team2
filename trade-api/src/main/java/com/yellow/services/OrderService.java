package com.yellow.services;

import java.time.Instant;
import java.util.UUID;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.entities.Account;
import com.yellow.entities.Instrument;
import com.yellow.entities.Order;
import com.yellow.entities.Position;
import com.yellow.enums.OrderSide;
import com.yellow.enums.Reason;
import com.yellow.exceptions.*;
import com.yellow.repositories.AccountRepository;
import com.yellow.repositories.InstrumentRepository;
import com.yellow.repositories.OrderRepository;
import com.yellow.repositories.PositionRepository;

import java.math.BigDecimal;

public class OrderService {

    private final AccountRepository accountRepo;
    private final InstrumentRepository instrumentRepo;
    private final PositionRepository positionRepo;
    private final OrderRepository orderRepo;

    public OrderService(AccountRepository accountRepo,
                        InstrumentRepository instrumentRepo,
                        PositionRepository positionRepo,
                        OrderRepository orderRepo) {
        this.accountRepo = accountRepo;
        this.instrumentRepo = instrumentRepo;
        this.positionRepo = positionRepo;
        this.orderRepo = orderRepo;
    }

    public Order placeOrder(PlaceOrderRequest request) {

        //account must exist
        Account account = accountRepo.findById(request.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.getAccountId()));

        //account must be active
        if (!account.isActive()) {
            throw new AccountNotActiveException(account.status());
        }

        //instrument must exist and be tradable
        //Instrument is UNKNOWN
        Instrument instrument = instrumentRepo.findBySymbol(request.getSymbol())
                .orElseThrow(() -> new InstrumentNotFoundException(
                        request.getSymbol(), Reason.UNKNOWN));
        //Instrument is NOT TRADEABLE
            if (!instrument.isTradable()) {
                throw new InstrumentNotFoundException(
                        request.getSymbol(), Reason.NOT_TRADABLE);
            }

        //Quantity is positive
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new InvalidOrderException("quantity",
                    String.valueOf(request.getQuantity()));
        }

        //price is greater than zero
        if (request.getPrice() == null || request.getPrice().signum() <= 0) {
            throw new InvalidOrderException("price",
                    String.valueOf(request.getPrice()));
        }

        //Insufficient Funds
        BigDecimal orderValue = BigDecimal.valueOf(request.getQuantity()).multiply(request.getPrice());
        if (request.getSide() == OrderSide.BUY
                && !account.canAfford(orderValue)) {
            throw new InsufficientFundsException(orderValue, account.availableFunds());
        }

        //Insufficient Holdings
        if (request.getSide() == OrderSide.SELL) {
            BigDecimal requestedQuantity = BigDecimal.valueOf(request.getQuantity());
            //Does accountId hold that instrument
            Position position = positionRepo.find(
                    account.accountId(),
                    instrument.instrumentId()
            ).orElseThrow(() -> new InsufficientHoldingsException(
                    requestedQuantity, BigDecimal.ZERO));
            //does account have enough quantity
            if (!position.canSell(requestedQuantity)) {
                throw new InsufficientHoldingsException(
                        requestedQuantity, position.quantity());
            }
        }

        //Idempotency Check
        if (orderRepo.existsByAccountAndKey(
                account.accountId(),
                request.getIdempotencyKey())) {
            throw new DuplicateOrderException(request.getIdempotencyKey(), null);
        }

        Order order = Order.place(
                account.accountId(),
                instrument.instrumentId(),
                request.getSide(),
                BigDecimal.valueOf(request.getQuantity()),
                request.getPrice(),
                request.getIdempotencyKey()
        );

        return orderRepo.save(order);




    }
    
    public Order cancelOrder(UUID orderId) {
    Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

    if (order.isTerminal()) {
        throw new OrderNotCancellableException(order.status());
    }

    order.cancel(Instant.now());

    return orderRepo.save(order);
    }
}