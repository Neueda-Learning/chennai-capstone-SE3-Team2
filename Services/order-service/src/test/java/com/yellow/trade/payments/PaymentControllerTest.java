package com.yellow.trade.payments;

import com.yellow.trade.controllers.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private PaymentService payments;

    private static String body(String amount, String key) {
        return "{\"amount\":" + amount + ",\"idempotencyKey\":\"" + key + "\"}";
    }

    private static final String KEY = "8f14e45f-ceea-467a-9575-ff1c2c1a3d5b";

    @Test
    @DisplayName("A deposit answers 202 with the PENDING transfer")
    void depositAccepted() throws Exception {
        when(payments.deposit(anyLong(), any())).thenReturn(new TransferResponse(7L, "DEPOSIT",
                new BigDecimal("5000.0000"), "PENDING", null, Instant.parse("2026-10-03T10:00:00Z"), null));

        mvc.perform(post("/api/v1/accounts/3/deposits").contentType("application/json").content(body("5000", KEY)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transferId").value(7))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("An amount that is zero, negative, or has more than two decimals, or no key, is VAL-422 and never reaches the service")
    void invalidRequests() throws Exception {
        for (String bad : new String[] {
                body("0", KEY), body("-10", KEY), body("10.555", KEY),
                "{\"amount\":10}", body("10", "short"),
                "{\"amount\":10,\"idempotencyKey\":\"" + KEY + "\",\"toAccount\":\"999\"}"}) {
            mvc.perform(post("/api/v1/accounts/3/withdrawals").contentType("application/json").content(bad))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        }
        verifyNoInteractions(payments);
    }

    @Test
    @DisplayName("A refusal answers in the platform envelope with its own code and status")
    void refusalEnvelope() throws Exception {
        when(payments.withdraw(anyLong(), any())).thenThrow(PaymentRefusedException.notEnoughAvailableCash());

        mvc.perform(post("/api/v1/accounts/3/withdrawals").contentType("application/json").content(body("5000", KEY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAY-400"));
    }
}
