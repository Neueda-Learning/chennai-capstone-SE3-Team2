package com.yellow.trade.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yellow.dto.PlaceOrderRequest;
import com.yellow.enums.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;

/**
 * A JSON body binds onto the domain's request DTO.
 *
 * This looks trivial and is not. PlaceOrderRequest has one constructor and no
 * no-arg constructor, so Jackson can bind to it only by matching parameter
 * names -- and it can only see those names if the domain was compiled with
 * -parameters.
 *
 * That flag was invisible while the domain was a folder of source inside this
 * service, because Spring Boot's parent supplies it. Compiled as its own jar
 * it has to be asked for, and the failure mode is not subtle: every
 * POST /api/v1/orders answers 500, and only at runtime.
 *
 * So this test exists to fail loudly in the build if the domain module ever
 * stops emitting parameter names, rather than at the review.
 */
class PlaceOrderRequestBindingTest {

    private static final String BODY = """
            {"accountId":3,"symbol":"ACME","side":"BUY",
             "quantity":100,"price":25.50,"idempotencyKey":"key-12345678"}
            """;

    @Test
    @DisplayName("a contract-shaped body binds onto the domain DTO")
    void bodyBindsOntoTheDomainDto() throws Exception {
        new ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                        .of(JacksonAutoConfiguration.class))
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    PlaceOrderRequest request = mapper.readValue(BODY, PlaceOrderRequest.class);

                    assertThat(request.getAccountId(), is(3L));
                    assertThat(request.getSymbol(), is("ACME"));
                    assertThat(request.getSide(), is(OrderSide.BUY));
                    assertThat(request.getQuantity(), is(100));
                    assertThat(request.getPrice(), comparesEqualTo(new BigDecimal("25.50")));
                    assertThat(request.getIdempotencyKey(), is("key-12345678"));
                });
    }

    @Test
    @DisplayName("the domain jar carries parameter names, which is what makes that possible")
    void theDomainJarCarriesParameterNames() throws NoSuchMethodException {
        var constructor = PlaceOrderRequest.class.getDeclaredConstructors()[0];

        assertThat("the domain module must compile with -parameters; see domainrules/pom.xml",
                constructor.getParameters()[0].isNamePresent(), is(true));
        assertThat(constructor.getParameters()[0].getName(), is("accountId"));
    }
}
