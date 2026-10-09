package com.yellow.trade.preferences;

import com.yellow.trade.controllers.GlobalExceptionHandler;
import com.yellow.trade.preferences.api.AlertChannel;
import com.yellow.trade.security.AccountNotReachableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreferencesController.class)
@Import(GlobalExceptionHandler.class)
class PreferencesControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private PreferenceService preferences;

    private static Preferences saved() {
        return new Preferences(3L, 3L, LandingScreen.MARKET_WATCH, AlertChannel.EMAIL, "r•••@example.com", true,
                Instant.parse("2026-10-06T10:00:00Z"));
    }

    @Test
    @DisplayName("answers the preferences in the contract's shape, the landing screen as the URL spells it")
    void reads() throws Exception {
        when(preferences.get(3L)).thenReturn(saved());

        mvc.perform(get("/api/v1/accounts/3/preferences"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"accountId":3,"defaultAccountId":3,"landingScreen":"market-watch","alertChannel":"EMAIL",
                         "contact":"r•••@example.com","stored":true,"updatedAt":"2026-10-06T10:00:00Z"}""", true));
    }

    @Test
    @DisplayName("saves what was sent")
    void saves() throws Exception {
        when(preferences.save(eq(3L), eq(new PreferencesUpdate(3L, LandingScreen.ORDERS, AlertChannel.IN_APP))))
                .thenReturn(saved());

        mvc.perform(put("/api/v1/accounts/3/preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultAccountId\":3,\"landingScreen\":\"orders\",\"alertChannel\":\"IN_APP\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a landing screen or a channel it does not know, or a missing field, is VAL-422 and saves nothing")
    void invalid() throws Exception {
        for (String body : new String[] {
                "{\"defaultAccountId\":3,\"landingScreen\":\"settings\",\"alertChannel\":\"EMAIL\"}",
                "{\"defaultAccountId\":3,\"landingScreen\":\"orders\",\"alertChannel\":\"SMS\"}",
                "{\"landingScreen\":\"orders\",\"alertChannel\":\"EMAIL\"}",
                "{\"defaultAccountId\":3,\"landingScreen\":\"orders\",\"alertChannel\":\"EMAIL\",\"email\":\"x@y.z\"}"}) {
            mvc.perform(put("/api/v1/accounts/3/preferences").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        }
        verifyNoInteractions(preferences);
    }

    @Test
    @DisplayName("another account is 403 ACC-403, Account not accessible")
    void anotherAccount() throws Exception {
        when(preferences.get(4L)).thenThrow(new AccountNotReachableException());
        when(preferences.save(eq(4L), any())).thenThrow(new AccountNotReachableException());

        mvc.perform(get("/api/v1/accounts/4/preferences"))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"errorCode\":\"ACC-403\",\"message\":\"Account not accessible\"}", true));
        mvc.perform(put("/api/v1/accounts/4/preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultAccountId\":4,\"landingScreen\":\"orders\",\"alertChannel\":\"EMAIL\"}"))
                .andExpect(status().isForbidden());
    }
}
