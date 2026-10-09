package com.yellow.executor.settle;

import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import com.yellow.executor.fill.FillDecision;
import com.yellow.executor.fill.OrderSnapshot;
import com.yellow.executor.persistence.AccountRow;
import com.yellow.executor.persistence.ExecutionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for the optimistic-lock exhaustion path. The integration test
 * covers the happy path against a real database.
 */
class FullSettlementTest {

    private final ExecutionMapper mapper = mock(ExecutionMapper.class);
    private final Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private final FullSettlement settlement = new FullSettlement(mapper, clock);

    @Test
    @DisplayName("lock exhausted after MAX_LOCK_ATTEMPTS: LockExhaustedException is thrown")
    void lockExhaustedAfterMaxAttempts() {
        UUID orderId = UUID.randomUUID();

        // The order moves to FILLED (settleIfNew returns 1)
        when(mapper.settleIfNew(any(), any(), any(), any(), any())).thenReturn(1);

        // Account read always returns the same version
        AccountRow account = new AccountRow();
        account.setVersion(1);
        account.setBalance(new BigDecimal("50000.00"));
        account.setBlockedFunds(new BigDecimal("4200.0000"));
        when(mapper.findAccount(any())).thenReturn(account);

        // CAS always fails: another writer is always faster
        when(mapper.updateAccount(any(), any(), any(), anyInt())).thenReturn(0);

        OrderSnapshot order = new OrderSnapshot(
                orderId, 3L, 1L, "ITC.NS",
                OrderSide.BUY, new BigDecimal("10"), new BigDecimal("420.00"),
                OrderStatus.NEW);

        // In production the @Transactional proxy catches this unchecked
        // exception and rolls back the settleIfNew UPDATE, leaving the order
        assertThrows(LockExhaustedException.class, () ->
                settlement.settle(new FillDecision.Fill(new BigDecimal("412.00")), order));
    }
}
