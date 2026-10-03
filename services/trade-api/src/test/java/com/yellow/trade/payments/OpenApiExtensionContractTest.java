package com.yellow.trade.payments;

import com.yellow.trade.instruments.InstrumentController;
import com.yellow.trade.instruments.InstrumentResponse;
import com.yellow.trade.onboarding.ApplicationReceived;
import com.yellow.trade.onboarding.ApplicationRequest;
import com.yellow.trade.onboarding.OnboardingController;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

/**
 * openapi/trade-api-extensions.yaml is what the trading UI generates its
 * client from, so it has to describe the code exactly. A route, a field or an
 * error code that drifts fails here, in the service's own build, before a
 * generated client could disagree with the server.
 */
class OpenApiExtensionContractTest {

    private static final List<Class<?>> CONTROLLERS =
            List.of(OnboardingController.class, InstrumentController.class, PaymentController.class);

    private static final Map<String, Class<? extends Record>> SCHEMAS = Map.of(
            "ApplicationRequest", ApplicationRequest.class,
            "ApplicationReceived", ApplicationReceived.class,
            "InstrumentResponse", InstrumentResponse.class,
            "BankAccountResponse", BankAccountResponse.class,
            "TransferRequest", TransferRequest.class,
            "TransferResponse", TransferResponse.class);

    @SuppressWarnings("unchecked")
    private static Map<String, Object> spec() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("openapi/trade-api-extensions.yaml"))) {
            return new Yaml().load(reader);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(Map<String, Object> spec, String name) {
        Map<String, Object> components = (Map<String, Object>) spec.get("components");
        return (Map<String, Object>) ((Map<String, Object>) components.get("schemas")).get(name);
    }

    @Test
    @DisplayName("Describes exactly the routes the onboarding, instrument and payment controllers serve")
    @SuppressWarnings("unchecked")
    void routes() throws IOException {
        Set<String> described = new TreeSet<>();
        ((Map<String, Map<String, Object>>) spec().get("paths")).forEach((path, operations) ->
                operations.keySet().forEach(verb -> described.add(verb.toUpperCase() + " " + path)));

        Set<String> served = new TreeSet<>();
        for (Class<?> controller : CONTROLLERS) {
            RequestMapping base = controller.getAnnotation(RequestMapping.class);
            String prefix = base == null ? "" : base.value()[0];
            for (Method method : controller.getDeclaredMethods()) {
                GetMapping get = method.getAnnotation(GetMapping.class);
                PostMapping post = method.getAnnotation(PostMapping.class);
                if (get != null) served.add("GET " + prefix + get.value()[0]);
                if (post != null) served.add("POST " + prefix + post.value()[0]);
            }
        }

        assertThat(described, is(served));
    }

    @Test
    @DisplayName("Every schema's properties are the record's fields, and its required list is what the record validates")
    @SuppressWarnings("unchecked")
    void schemas() throws IOException {
        Map<String, Object> spec = spec();
        SCHEMAS.forEach((name, record) -> {
            Map<String, Object> schema = schema(spec, name);
            Set<String> fields = Arrays.stream(record.getRecordComponents())
                    .map(RecordComponent::getName).collect(Collectors.toCollection(TreeSet::new));

            assertThat(name + " properties", new TreeSet<>(((Map<String, Object>) schema.get("properties")).keySet()), is(fields));

            if (name.endsWith("Request")) {
                Set<String> validatedRequired = Arrays.stream(record.getRecordComponents())
                        // Jakarta's constraints do not target record components, so the
                        // compiler puts them on the accessor; read them there.
                        .filter(c -> c.getAccessor().isAnnotationPresent(NotBlank.class)
                                || c.getAccessor().isAnnotationPresent(NotNull.class))
                        .map(RecordComponent::getName).collect(Collectors.toCollection(TreeSet::new));
                assertThat(name + " required", new TreeSet<>((List<String>) schema.get("required")), is(validatedRequired));
            }
        });
    }

    @Test
    @DisplayName("Lists every error code the payments module can refuse with")
    @SuppressWarnings("unchecked")
    void errorCodes() throws IOException {
        Map<String, Object> errorCode = (Map<String, Object>)
                ((Map<String, Object>) schema(spec(), "ErrorResponse").get("properties")).get("errorCode");
        Set<String> listed = new HashSet<>((List<String>) errorCode.get("enum"));

        assertThat(listed, hasItems(
                PaymentRefusedException.notEnoughAvailableCash().code(),
                PaymentRefusedException.noBankAccount().code(),
                PaymentRefusedException.keyReused().code(),
                "RATE-429", "ACC-403", "ACC-404", "VAL-422", "AUTH-401"));
    }
}
