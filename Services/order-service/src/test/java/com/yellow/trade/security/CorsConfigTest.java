package com.yellow.trade.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

class CorsConfigTest {

    private static final String UI = "http://localhost:4200";

    private final CorsFilter filter = new CorsConfig().corsFilter(List.of(UI)).getFilter();

    private static MockHttpServletRequest preflight(String origin) {
        return preflight(origin, "POST", "/api/v1/orders");
    }

    private static MockHttpServletRequest preflight(String origin, String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", method);
        request.addHeader("Access-Control-Request-Headers", "authorization, content-type");
        return request;
    }

    @Test
    @DisplayName("Answers the UI's preflight itself, allowing Authorization, and never reaches the token filter")
    void preflightFromTheUi() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(preflight(UI), response, chain);

        assertThat(response.getStatus(), is(200));
        assertThat(response.getHeader("Access-Control-Allow-Origin"), is(UI));
        assertThat(response.getHeader("Access-Control-Allow-Headers"), containsString("authorization"));
        assertThat(chain.getRequest(), is(nullValue()));
    }

    @Test
    @DisplayName("Allows a PUT from the UI: Settings saves the customer's preferences with one")
    void preflightForAPut() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(preflight(UI, "PUT", "/api/v1/accounts/3/preferences"), response, new MockFilterChain());

        assertThat(response.getStatus(), is(200));
        assertThat(response.getHeader("Access-Control-Allow-Methods"), containsString("PUT"));
    }

    @Test
    @DisplayName("Refuses a preflight from any other origin")
    void preflightFromElsewhere() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(preflight("https://evil.example"), response, new MockFilterChain());

        assertThat(response.getStatus(), is(403));
        assertThat(response.getHeader("Access-Control-Allow-Origin"), is(nullValue()));
    }

    @Test
    @DisplayName("Marks the UI's real request and passes it on; sends no credentials header")
    void realRequestFromTheUi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/3/orders");
        request.addHeader("Origin", UI);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("Access-Control-Allow-Origin"), is(UI));
        assertThat(response.getHeader("Access-Control-Allow-Credentials"), is(nullValue()));
        assertThat(chain.getRequest(), is(notNullValue()));
    }

    @Test
    @DisplayName("Leaves a request with no Origin -- curl, the executor -- untouched")
    void notCrossOrigin() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/accounts/3"), response, chain);

        assertThat(response.getHeader("Access-Control-Allow-Origin"), is(nullValue()));
        assertThat(chain.getRequest(), is(notNullValue()));
    }

    @Test
    @DisplayName("Runs before the token filter, which would otherwise refuse the preflight for carrying no token")
    void ordering() {
        int cors = new CorsConfig().corsFilter(List.of(UI)).getOrder();
        int token = new SecurityConfig()
                .jwtAuthenticationFilter(mock(JwtTokenVerifier.class), new ObjectMapper(),
                        new JwtProperties("s".repeat(32), "HS256", "auth-service", "/api/v1/"))
                .getOrder();

        assertThat(cors, lessThan(token));
    }
}
