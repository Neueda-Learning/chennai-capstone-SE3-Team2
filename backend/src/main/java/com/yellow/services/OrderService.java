package com.yellow;

import com.yellow.dto.PlaceOrderRequest;
import com.yellow.entities.Account;
import com.yellow.entities.Instrument;
import com.yellow.entities.Order;
import com.yellow.entities.Position;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.exceptions.InstrumentNotFoundException;
import com.yellow.exceptions.InvalidOrderException;
import com.yellow.repositories.AccountRepository;
import com.yellow.repositories.InstrumentRepository;
import com.yellow.repositories.OrderRepository;
import com.yellow.repositories.PositionRepository;

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
                .orElseThrow(AccountNotFoundException::new);

        //account must be active
        if (!account.isActive()) {
            throw new AccountNotActiveException();
        }
//
//        //instrument must exist and be tradable
//        //Instrument is UNKNOWN
        Instrument instrument = instrumentRepo.findBySymbol(request.getSymbol())
                .orElseThrow(InstrumentNotFoundException::new);
//        //Instrument is NOT TRADEABLE
            if (!instrument.isTradable()) {
                throw new InstrumentNotFoundException();
            }
//
//        //Quantity is positive
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new InvalidOrderException();
        }

        return null;
    }
}