package com.yellow.trade.onboarding;

import com.yellow.trade.controllers.GlobalExceptionHandler;
import com.yellow.trade.onboarding.OnboardingService.DuplicateApplicationException;
import com.yellow.trade.onboarding.OnboardingService.InvalidApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OnboardingController.class)
@Import(GlobalExceptionHandler.class)
@ExtendWith(OutputCaptureExtension.class)
class OnboardingControllerTest {

    private static final String PAN = "ABCPM1234Q";
    private static final String EMAIL = "priya.distinctive@example.com";
    private static final String PHONE = "+919812345611";
    private static final String DOB = "1990-05-17";
    private static final String BANK = "509876543210";
    private static final String IFSC = "DEMO0000001";

    private static String body(String pan, String dob, String phone) {
        return body(pan, dob, phone, BANK, IFSC);
    }

    private static String body(String pan, String dob, String phone, String bank, String ifsc) {
        return """
                {"name":"Priya Menon","dob":"%s","email":"%s","phoneNumber":"%s","pan":"%s","address":"12 Anna Nagar","bankAccountNumber":"%s","ifsc":"%s"}
                """.formatted(dob, EMAIL, phone, pan, bank, ifsc);
    }

    private static final String VALID = body(PAN, DOB, PHONE);
    private static final String RECEIVED = """
            {"status":"RECEIVED","message":"Application received. If your details are verified, you will receive an email to activate your account."}""";

    @Autowired private MockMvc mvc;
    @MockitoBean private OnboardingService onboarding;
    @MockitoBean private ApplicationRateLimiter limiter;

    @BeforeEach
    void allowed() {
        when(limiter.tryAcquire(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("A valid application answers 202 with the fixed body and no identifier")
    void validApplication() throws Exception {
        when(onboarding.apply(any())).thenReturn(11L);

        mvc.perform(post("/onboarding/applications").contentType("application/json").content(VALID))
                .andExpect(status().isAccepted())
                .andExpect(content().json(RECEIVED, true));
    }

    @Test
    @DisplayName("A duplicate answers byte-for-byte the same as a new customer")
    void duplicateIsIndistinguishable() throws Exception {
        when(onboarding.apply(any())).thenReturn(11L);
        String fresh = mvc.perform(post("/onboarding/applications").contentType("application/json").content(VALID))
                .andReturn().getResponse().getContentAsString();

        when(onboarding.apply(any())).thenThrow(new DuplicateApplicationException());
        String duplicate = mvc.perform(post("/onboarding/applications").contentType("application/json").content(VALID))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(duplicate, is(fresh));
    }

    @Test
    @DisplayName("Malformed input answers VAL-422 and never reaches the service")
    void malformedInput() throws Exception {
        for (String bad : new String[] {
                body("abcpm1234q", DOB, PHONE),      // PAN in lowercase
                body(PAN, "17-05-1990", PHONE),      // date in the wrong format
                body(PAN, DOB, "9812345611"),        // phone without +91
                body(PAN, DOB, PHONE, "12345", IFSC),           // account number too short
                body(PAN, DOB, PHONE, "5098765432AB", IFSC),    // account number not all digits
                body(PAN, DOB, PHONE, BANK, "DEMO1000001"),     // IFSC without the 0 in fifth place
                VALID.replace(",\"bankAccountNumber\":\"" + BANK + "\"", ""),  // no bank account
                VALID.replace("}", ",\"isAdmin\":true}")}) {
            mvc.perform(post("/onboarding/applications").contentType("application/json").content(bad))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        }
        verifyNoInteractions(onboarding);
    }

    @Test
    @DisplayName("A date that is well formed but impossible or in the future answers VAL-422")
    void invalidDate() throws Exception {
        when(onboarding.apply(any())).thenThrow(new InvalidApplicationException("dob is in the future"));

        mvc.perform(post("/onboarding/applications").contentType("application/json").content(VALID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    @DisplayName("Over the limit answers RATE-429 and creates nothing")
    void rateLimited() throws Exception {
        when(limiter.tryAcquire(anyString())).thenReturn(false);

        mvc.perform(post("/onboarding/applications").contentType("application/json").content(VALID))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE-429"));
        verifyNoInteractions(onboarding);
    }

    @Test
    @DisplayName("Nothing personal reaches the log, on any path")
    void noPersonalDataLogged(CapturedOutput output) throws Exception {
        when(onboarding.apply(any())).thenReturn(11L);
        mvc.perform(post("/onboarding/applications").contentType("application/json").content(VALID));
        when(onboarding.apply(any())).thenThrow(new DuplicateApplicationException());
        mvc.perform(post("/onboarding/applications").contentType("application/json").content(VALID));
        mvc.perform(post("/onboarding/applications").contentType("application/json").content(body(PAN, "17-05-1990", PHONE)));
        mvc.perform(post("/onboarding/applications").contentType("application/json").content(body("ABCPM12", DOB, PHONE)));
        mvc.perform(post("/onboarding/applications").contentType("application/json").content(body(PAN, DOB, PHONE, "5098765", IFSC)));

        String logged = output.getAll();
        // The capture really saw each path, so the absences below mean something.
        assertThat(logged, containsString("application accepted: client 11"));
        assertThat(logged, containsString("application not created"));
        assertThat(logged, containsString("VAL-422"));
        for (String personal : new String[] {PAN, EMAIL, PHONE, DOB, "17-05-1990", "ABCPM12", "Priya Menon", BANK, "5098765"}) {
            assertThat("log contains " + personal, logged, not(containsString(personal)));
        }
    }
}
