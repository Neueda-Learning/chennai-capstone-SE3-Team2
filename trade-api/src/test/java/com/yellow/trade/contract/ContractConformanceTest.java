package com.yellow.trade.contract;

import com.yellow.enums.AccountStatus;
import com.yellow.enums.OrderSide;
import com.yellow.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads contracts/trade-api.yaml and asserts the Java agrees with it.
 *
 * The contract is the specification, and a specification nothing checks drifts
 * from the code within a sprint. These assertions are deliberately about the
 * parts where drift is silent and expensive: a renamed enum literal breaks the
 * contract, the database and the generated Angular types at once, and a
 * catalogue code that exists in one place and not the other is a client
 * branching on something the server never sends.
 *
 * Parsed with regular expressions rather than an OpenAPI library on purpose:
 * the test must not need a dependency the service does not otherwise have,
 * and the shapes it reads are small and fixed.
 */
class ContractConformanceTest {

    private static final Path CONTRACT = Path.of("..", "contracts", "trade-api.yaml");

    private static String contract() throws IOException {
        assertTrue(Files.exists(CONTRACT),
                "contracts/trade-api.yaml is the specification and must be in the repository");
        return Files.readString(CONTRACT, StandardCharsets.UTF_8);
    }

    /** Pulls the inline "enum: [A, B, C]" list that follows a named schema. */
    private static List<String> enumLiteralsOf(String schemaName) throws IOException {
        Matcher matcher = Pattern.compile(
                        "\\n    " + schemaName + ":\\n(?:.*\\n)*?\\s*enum: \\[([^\\]]+)\\]")
                .matcher(contract());
        assertTrue(matcher.find(), schemaName + " is not declared in the contract");
        return Arrays.stream(matcher.group(1).split(","))
                .map(String::trim)
                .toList();
    }

    @Test
    @DisplayName("AccountStatus matches the contract exactly, no more and no fewer")
    void accountStatusMatches() throws IOException {
        assertThat(enumLiteralsOf("AccountStatus"),
                is(Arrays.stream(AccountStatus.values()).map(Enum::name).toList()));
    }

    @Test
    @DisplayName("OrderSide matches the contract exactly")
    void orderSideMatches() throws IOException {
        assertThat(enumLiteralsOf("OrderSide"),
                is(Arrays.stream(OrderSide.values()).map(Enum::name).toList()));
    }

    @Test
    @DisplayName("OrderStatus matches the contract exactly, CANCELLED spelled with two Ls")
    void orderStatusMatches() throws IOException {
        List<String> fromContract = enumLiteralsOf("OrderStatus");

        assertThat(fromContract,
                is(Arrays.stream(OrderStatus.values()).map(Enum::name).toList()));
        assertThat(fromContract, hasItem("CANCELLED"));
        // There is no partial-fill literal: the executor fills in full or rejects.
        assertThat(fromContract.size(), is(4));
    }

    @Test
    @DisplayName("every code the handler can emit is declared in the contract's ErrorResponse")
    void everyEmittedCodeIsDeclared() throws IOException {
        Set<String> declared = declaredErrorCodes();

        // Scraped from the handler so that adding a code without declaring it
        // in the contract fails here rather than at Sprint 9's integration.
        Set<String> emitted = codesEmittedByTheHandler();

        assertThat("a code is emitted that the contract does not declare",
                emitted, everyItem(org.hamcrest.Matchers.in(declared)));
    }

    @Test
    @DisplayName("all seven catalogue codes appear in the contract")
    void everyCatalogueCodeIsDeclared() throws IOException {
        Set<String> declared = declaredErrorCodes();

        for (String code : List.of("ACC-404", "ACC-403", "INS-404",
                "ORD-400", "ORD-409", "VAL-422", "AUTH-401")) {
            assertThat("catalogue code missing from the contract: " + code,
                    declared, hasItem(code));
        }
    }

    @Test
    @DisplayName("the contract fixes all six operations")
    void allSixOperationsAreDeclared() throws IOException {
        String yaml = contract();

        for (String operationId : List.of("getAccount", "getBalance", "getPositions",
                "getOrderHistory", "placeOrder", "cancelOrder")) {
            assertTrue(yaml.contains("operationId: " + operationId),
                    "operation missing from the contract: " + operationId);
        }
    }

    @Test
    @DisplayName("the error envelope admits no field beyond errorCode and message")
    void envelopeIsClosed() throws IOException {
        Matcher matcher = Pattern.compile(
                        "\\n    ErrorResponse:\\n(?:.*\\n)*?\\s*required: \\[([^\\]]+)\\]")
                .matcher(contract());
        assertTrue(matcher.find(), "ErrorResponse is not declared in the contract");

        assertThat(Arrays.stream(matcher.group(1).split(",")).map(String::trim).toList(),
                is(List.of("errorCode", "message")));
        assertTrue(contract().contains("additionalProperties: false"),
                "the envelope must be closed, or a client can be sent fields it cannot parse");
    }

    private static Set<String> declaredErrorCodes() throws IOException {
        // The enum list under ErrorResponse.properties.errorCode, read as the
        // block between "enum:" and the next property at a shallower indent.
        Matcher block = Pattern.compile(
                        "    ErrorResponse:\\n(?:.*\\n)*?          enum:\\n((?:            - .*\\n)+)")
                .matcher(contract());
        assertTrue(block.find(), "ErrorResponse declares no errorCode enum");

        Matcher codes = Pattern.compile("[A-Z]{3,4}-\\d{3}").matcher(block.group(1));
        Set<String> declared = new LinkedHashSet<>();
        while (codes.find()) {
            declared.add(codes.group());
        }
        return declared;
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
        // The transport-mismatch codes are the handler's own, outside the
        // business catalogue, and the contract does not declare them.
        found.removeAll(Set.of("REQ-404", "REQ-405", "REQ-415", "SRV-500"));
        return found;
    }
}
