package com.yellow.trade.kyc;

/**
 * The KYC module's published seam. Onboarding depends on this interface and on
 * nothing else in the package, so a vendor's implementation can replace ours
 * without the caller changing.
 *
 * Asynchronous by contract: {@link #submit} records the request and returns at
 * once, and the decision comes later. Every real KYC provider works that way,
 * and a synchronous interface would have to be rewritten the day one is
 * plugged in.
 */
public interface KycService {

    /**
     * Queues a customer for verification. Must run inside the caller's
     * transaction, so the customer and their pending check are created
     * together or not at all.
     */
    void submit(long clientId);
}
