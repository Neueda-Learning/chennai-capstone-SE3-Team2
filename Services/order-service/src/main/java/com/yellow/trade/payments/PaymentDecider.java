package com.yellow.trade.payments;

import com.yellow.trade.mappers.BankAccountRow;
import com.yellow.trade.mappers.PaymentMapper;
import com.yellow.trade.mappers.TransferRow;
import com.yellow.trade.payments.PaymentGateway.Decision;
import com.yellow.trade.payments.PaymentGateway.Direction;
import com.yellow.trade.payments.PaymentGateway.Instruction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Has the gateway decide one transfer and applies the outcome. The decision
 * and the money move commit together or not at all:
 *
 * <ul>
 *   <li>deposit, SUCCESS -- the amount is credited</li>
 *   <li>withdrawal, SUCCESS -- the held amount leaves the account</li>
 *   <li>withdrawal, FAILED -- the hold is released</li>
 *   <li>deposit, FAILED -- nothing moves</li>
 * </ul>
 */
@Service
class PaymentDecider {

    private final PaymentMapper payments;
    private final PaymentGateway gateway;

    PaymentDecider(PaymentMapper payments, PaymentGateway gateway) {
        this.payments = payments;
        this.gateway = gateway;
    }

    /**
     * The stub answers at once, so the gateway is called inside the
     * transaction; a real one's network call would move out of it, and the
     * guarded update below already makes that safe.
     *
     * @return SUCCESS or FAILED, or empty when another run decided it first
     */
    @Transactional
    Optional<String> decide(long transferId) {
        TransferRow transfer = payments.findById(transferId);
        if (transfer == null) {
            throw new IllegalStateException("no transfer " + transferId);
        }
        if (!"PENDING".equals(transfer.getStatus())) {
            return Optional.empty();
        }
        BankAccountRow bank = payments.findBankAccount(transfer.getClientId());
        if (bank == null) {
            throw new IllegalStateException("transfer " + transferId + " has no bank account to move money with");
        }

        Direction direction = Direction.valueOf(transfer.getDirection());
        Decision decision = gateway.process(new Instruction(transferId, direction, transfer.getAmount(),
                bank.getAccountNumber(), bank.getIfsc(), bank.getHolderName()));
        String status = decision.succeeded() ? "SUCCESS" : "FAILED";

        if (payments.decide(transferId, status, decision.reason(), decision.reference()) == 0) {
            return Optional.empty();
        }
        int moved = switch (direction) {
            case DEPOSIT -> decision.succeeded() ? payments.credit(transfer.getClientId(), transfer.getAmount()) : 1;
            case WITHDRAWAL -> decision.succeeded()
                    ? payments.settleWithdrawal(transfer.getClientId(), transfer.getAmount())
                    : payments.releaseHold(transfer.getClientId(), transfer.getAmount());
        };
        if (moved == 0) {
            // Throwing rolls the decision back with it; the transfer stays PENDING.
            throw new IllegalStateException("transfer " + transferId + " found no account to move money on");
        }
        return Optional.of(status);
    }
}
