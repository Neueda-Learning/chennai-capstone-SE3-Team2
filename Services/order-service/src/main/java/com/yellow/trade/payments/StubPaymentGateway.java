package com.yellow.trade.payments;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Stands in for a payment gateway, the way the KYC stub stands in for a KYC
 * vendor. Deterministic, so a demonstration can produce either outcome on
 * demand: any single transfer over ₹2,00,000 is declined; everything else
 * goes through.
 */
@Component
class StubPaymentGateway implements PaymentGateway {

    static final BigDecimal PER_TRANSFER_LIMIT = new BigDecimal("200000");
    static final String OVER_LIMIT = "over the ₹2,00,000 per-transfer limit";

    @Override
    public Decision process(Instruction instruction) {
        String reference = "STUB-%08d".formatted(instruction.transferId());
        if (instruction.amount().compareTo(PER_TRANSFER_LIMIT) > 0) {
            return Decision.failure(reference, OVER_LIMIT);
        }
        return Decision.success(reference);
    }
}
