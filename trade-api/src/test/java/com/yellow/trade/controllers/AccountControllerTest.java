package com.yellow.trade.controllers;

import com.yellow.enums.AccountStatus;
import com.yellow.enums.OrderStatus;
import com.yellow.exceptions.AccountNotActiveException;
import com.yellow.exceptions.AccountNotFoundException;
import com.yellow.trade.dto.AccountResponse;
import com.yellow.trade.dto.BalanceResponse;
import com.yellow.trade.dto.PositionResponse;
import com.yellow.trade.services.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AccountService accountService;

    @Test
    @DisplayName("the account body carries the string reference under accountId")
    void accountBodyMatchesTheContract() throws Exception {
        when(accountService.getAccount(3L)).thenReturn(
                new AccountResponse("ACC-000003", "Rohan Nair", AccountStatus.ACTIVE, NOW));

        mockMvc.perform(get("/api/v1/accounts/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC-000003"))
                .andExpect(jsonPath("$.holderName").value("Rohan Nair"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                // Money belongs to /balance, and the lock version is internal.
                .andExpect(jsonPath("$.cashBalance").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    @DisplayName("the balance body carries cash, blocked and available")
    void balanceBodyMatchesTheContract() throws Exception {
        when(accountService.getBalance(3L)).thenReturn(new BalanceResponse(
                3L, new BigDecimal("750000.0000"), new BigDecimal("220000.0000"),
                new BigDecimal("530000.0000"), "INR", NOW));

        mockMvc.perform(get("/api/v1/accounts/3/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(3))
                .andExpect(jsonPath("$.availableFunds").value(530000.0000))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    @DisplayName("a holding keeps its position type and its fractional quantity")
    void positionsKeepTypeAndFraction() throws Exception {
        when(accountService.getPositions(5L)).thenReturn(List.of(new PositionResponse(
                5L, "SCH100002", "DELIVERY", new BigDecimal("240.117000"), new BigDecimal("41.6500"))));

        mockMvc.perform(get("/api/v1/accounts/5/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].positionType").value("DELIVERY"))
                // A mutual fund allotment is not a whole number of units.
                .andExpect(jsonPath("$[0].quantity").value(240.117000));
    }

    @Test
    @DisplayName("an unknown account is ACC-404 in the envelope")
    void unknownAccountIsAcc404() throws Exception {
        when(accountService.getAccount(999L)).thenThrow(new AccountNotFoundException(999L));

        mockMvc.perform(get("/api/v1/accounts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACC-404"))
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    @DisplayName("an unreachable account is ACC-403 and names no key in the body")
    void unreachableAccountIsAcc403() throws Exception {
        when(accountService.getAccount(4L)).thenThrow(new AccountNotActiveException(AccountStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/accounts/4"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"))
                .andExpect(jsonPath("$.message").value("Account not active"));
    }

    @Test
    @DisplayName("the three history filters reach the service as typed values")
    void historyFiltersAreBoundAndTyped() throws Exception {
        when(accountService.getOrders(anyLong(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts/3/orders")
                        .param("status", "NEW")
                        .param("from", "2026-09-01T00:00:00Z")
                        .param("to", "2026-09-30T00:00:00Z"))
                .andExpect(status().isOk());

        verify(accountService).getOrders(eq(3L), eq(OrderStatus.NEW),
                eq(Instant.parse("2026-09-01T00:00:00Z")),
                eq(Instant.parse("2026-09-30T00:00:00Z")));
    }

    @Test
    @DisplayName("a status filter outside the enum is VAL-422, never reaching a statement")
    void unknownStatusFilterIsVal422() throws Exception {
        // The injection attempt a ${} mapper would have executed.
        mockMvc.perform(get("/api/v1/accounts/3/orders").param("status", "NEW' OR '1'='1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    @DisplayName("an account id below one is VAL-422")
    void nonPositiveAccountIdIsVal422() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/-5"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }
}
