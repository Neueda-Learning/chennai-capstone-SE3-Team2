package com.yellow.trade.payments;

import java.math.BigDecimal;

/**
 * Where a real payment gateway plugs in. It is told what to move, which way,
 * and the customer's registered bank account -- nothing else about them.
 */
interface PaymentGateway {

    Decision process(Instruction instruction);

    enum Direction { DEPOSIT, WITHDRAWAL }

    record Instruction(long transferId, Direction direction, BigDecimal amount,
                       String accountNumber, String ifsc, String holderName) {

        /** Deliberately not the fields: an accidental log line must not print the account number. */
        @Override
        public String toString() {
            return "Instruction[transfer " + transferId + ", " + direction + "]";
        }
    }

    /**
     * @param reference the gateway's own reference for the payment, kept for reconciliation
     * @param reason    why it failed; null when it succeeded
     */
    record Decision(boolean succeeded, String reference, String reason) {

        static Decision success(String reference) {
            return new Decision(true, reference, null);
        }

        static Decision failure(String reference, String reason) {
            return new Decision(false, reference, reason);
        }
    }
}
