package com.yellow.trade.advice;

import com.yellow.trade.controllers.GlobalExceptionHandler;
import com.yellow.trade.security.AccountNotReachableException;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountAdviceController.class)
@Import(GlobalExceptionHandler.class)
class AccountAdviceControllerTest {

    private static final Instant AT = Instant.parse("2026-10-07T04:00:00Z");

    @Autowired private MockMvc mvc;
    @MockitoBean private AccountAdviceService advice;

    @Test
    @DisplayName("the account's held and watched stocks in the contract's shape: a signal with its reason, or none with why")
    void list() throws Exception {
        Signal buy = new Signal("ITC.NS", Direction.BUY, 64, Methodology.NAME, "The 20-day average is 2.0% above the 50-day and RSI is 61, so the trend is up.",
                new SignalFigures(new BigDecimal("102.0"), new BigDecimal("100.0"), new BigDecimal("61.00"), new BigDecimal("103.00"), AT, 80),
                AT, AdviceService.DISCLAIMER);
        Signal none = new Signal("122639", null, null, Methodology.NAME,
                "A fund is priced once a day at its NAV, and this method needs daily candles, so there is no signal.", null, AT,
                AdviceService.DISCLAIMER);
        when(advice.forAccount(3L)).thenReturn(new AccountAdvice(3L,
                List.of(new AdviceItem("ITC.NS", true, true, buy), new AdviceItem("122639", true, false, none)), false,
                AdviceService.DISCLAIMER));

        mvc.perform(get("/api/v1/accounts/3/advice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(3))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.items[0].held").value(true))
                .andExpect(jsonPath("$.items[0].watched").value(true))
                .andExpect(jsonPath("$.items[0].signal.direction").value("BUY"))
                .andExpect(jsonPath("$.items[0].signal.reason").value("The 20-day average is 2.0% above the 50-day and RSI is 61, so the trend is up."))
                .andExpect(jsonPath("$.items[1].signal.direction").doesNotExist())
                .andExpect(jsonPath("$.items[1].signal.strength").doesNotExist())
                .andExpect(content().json("""
                        {"items":[{},{"symbol":"122639","held":true,"watched":false,
                          "signal":{"direction":null,"strength":null,"figures":null,
                                    "reason":"A fund is priced once a day at its NAV, and this method needs daily candles, so there is no signal."}}]}"""));
    }

    @Test
    @DisplayName("another customer's account is 403 ACC-403")
    void otherAccount() throws Exception {
        when(advice.forAccount(4L)).thenThrow(new AccountNotReachableException());

        mvc.perform(get("/api/v1/accounts/4/advice"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"));
    }
}
