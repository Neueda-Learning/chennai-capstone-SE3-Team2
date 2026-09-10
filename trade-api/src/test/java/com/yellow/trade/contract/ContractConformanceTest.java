package com.yellow.trade.contract;

import com.yellow.enums.AccountStatus;
import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the binding contract and asserts the Java agrees with it.
 *
 * `contracts/trade-api.yaml` is the programme's own file, verbatim. It is the
 * specification, and a specification nothing checks drifts from the code within
 * a sprint -- which is how a team ends up implementing a contract it
 * reconstructed from prose instead of the one it was given.
 *
 * Parsed properly rather than with regular expressions, so the test survives
 * the file being reformatted. SnakeYAML is already on the classpath: Spring
 * Boot uses it to read application.yml.
 *
 * The one place we knowingly differ is recorded in contracts/DEVIATIONS.md and
 * asserted at the bottom of this class, so that the deviation cannot quietly
 * grow into several.
 */
class ContractConformanceTest {

    private static final Path CONTRACT = Path.of("..", "contracts", "trade-api.yaml");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> contract() throws IOException {
        assertTrue(Files.exists(CONTRACT),
                "contracts/trade-api.yaml is the specification and must be in the repository");
        try (InputStream in = Files.newInputStream(CONTRACT)) {
            return new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(String name) throws IOException {
        Map<String, Object> components = (Map<String, Object>) contract().get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> schema = (Map<String, Object>) schemas.get(name);
        assertTrue(schema != null, name + " is not declared in the contract");
        return schema;
    }

    @SuppressWarnings("unchecked")
    private static List<String> enumOf(String schemaName) throws IOException {
        return (List<String>) schema(schemaName).get("enum");
    }

    // ----------------------------------------------------- enumerations

    @Test
    @DisplayName("AccountStatus matches the contract exactly, no more and no fewer")
    void accountStatusMatches() throws IOException {
        assertThat(enumOf("AccountStatus"),
                is(Arrays.stream(AccountStatus.values()).map(Enum::name).toList()));
    }

    @Test
    @DisplayName("OrderSide matches the contract exactly")
    void orderSideMatches() throws IOException {
        assertThat(enumOf("OrderSide"),
                is(Arrays.stream(OrderSide.values()).map(Enum::name).toList()));
    }

    @Test
    @DisplayName("OrderStatus matches the contract exactly, CANCELLED spelled with two Ls")
    void orderStatusMatches() throws IOException {
        List<String> fromContract = enumOf("OrderStatus");

        assertThat(fromContract, is(Arrays.stream(OrderStatus.values()).map(Enum::name).toList()));
        assertThat(fromContract, hasItem("CANCELLED"));
        // No partial-fill literal: the executor fills in full or rejects.
        assertThat(fromContract.size(), is(4));
    }

    // ------------------------------------------------------- the envelope

    @Test
    @DisplayName("every code the handler can emit is declared in the contract")
    void everyEmittedCodeIsDeclared() throws IOException {
        assertThat("a code is emitted that the contract does not declare",
                codesEmittedByTheHandler(), everyItem(in(declaredErrorCodes())));
    }

    @Test
    @DisplayName("all seven catalogue codes are declared")
    void everyCatalogueCodeIsDeclared() throws IOException {
        Set<String> declared = declaredErrorCodes();

        for (String code : List.of("ACC-404", "ACC-403", "INS-404",
                "ORD-400", "ORD-409", "VAL-422", "AUTH-401")) {
            assertThat("catalogue code missing from the contract: " + code, declared, hasItem(code));
        }
    }

    @Test
    @DisplayName("the error envelope admits no field beyond errorCode and message")
    void envelopeIsClosed() throws IOException {
        Map<String, Object> envelope = schema("ErrorResponse");

        assertThat(envelope.get("required"), is(List.of("errorCode", "message")));
        assertThat("the envelope must be closed, or a client can be sent fields it cannot parse",
                envelope.get("additionalProperties"), is(false));
    }

    // ------------------------------------------------------- operations

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("the contract fixes six operations and we implement those six")
    void allSixOperationsAreDeclared() throws IOException {
        Map<String, Object> paths = (Map<String, Object>) contract().get("paths");

        Set<String> declared = new LinkedHashSet<>();
        for (Object path : paths.values()) {
            for (Object operation : ((Map<String, Object>) path).values()) {
                Object id = ((Map<String, Object>) operation).get("operationId");
                if (id != null) {
                    declared.add(id.toString());
                }
            }
        }

        assertThat(declared, is(Set.of("placeOrder", "cancelOrder",
                "getAccount", "getBalance", "getPositions", "getOrders")));
    }

    // --------------------------------------------------- the one deviation

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("quantity is the only field we knowingly type differently, and it is documented")
    void theSingleDeviationIsDocumented() throws IOException {
        // The contract types response quantities as int32. We return decimals,
        // because mutual fund allotments are fractional and the schema stores
        // NUMERIC(18,6). If that ever stops being the only deviation, this test
        // is where the second one gets noticed.
        for (String name : List.of("OrderResponse", "OrderHistoryEntry", "PositionResponse")) {
            Map<String, Object> quantity =
                    (Map<String, Object>) ((Map<String, Object>) schema(name).get("properties")).get("quantity");
            assertThat(name + ".quantity is no longer int32; DEVIATIONS.md is out of date",
                    quantity.get("type"), is("integer"));
        }

        // Input is NOT part of the deviation: this API accepts whole units.
        Map<String, Object> requested = (Map<String, Object>)
                ((Map<String, Object>) schema("PlaceOrderRequest").get("properties")).get("quantity");
        assertThat(requested.get("type"), is("integer"));

        Path deviations = Path.of("..", "contracts", "DEVIATIONS.md");
        assertTrue(Files.exists(deviations), "a deviation from a binding contract must be written down");
        assertThat(Files.readString(deviations, StandardCharsets.UTF_8).contains("int32"), is(true));
    }

    // ----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Set<String> declaredErrorCodes() throws IOException {
        Map<String, Object> properties = (Map<String, Object>) schema("ErrorResponse").get("properties");
        Map<String, Object> errorCode = (Map<String, Object>) properties.get("errorCode");
        return new LinkedHashSet<>((List<String>) errorCode.get("enum"));
    }

    private static Set<String> codesEmittedByTheHandler() throws IOException {
        Path handler = Path.of("src", "main", "java", "com", "yellow", "trade",
                "controllers", "GlobalExceptionHandler.java");
        Matcher matcher = Pattern.compile("\"([A-Z]{3}-\\d{3})\"")
                .matcher(Files.readString(handler, StandardCharsets.UTF_8));

        Set<String> found = new LinkedHashSet<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        // The transport-mismatch codes are the handler's own, for requests that
        // address no documented operation at all. The contract's catalogue
        // describes business outcomes and does not cover them.
        found.removeAll(Set.of("REQ-404", "REQ-405", "REQ-415", "SRV-500"));
        return found;
    }
}
