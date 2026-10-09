package com.yellow.trade.modules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

/**
 * The Sprint 10 module boundaries, held by the build rather than by review
 * alone: in one Maven project no compiler stops a module reaching into
 * another (decision log 0001).
 *
 * <ul>
 *   <li>Nothing outside a module refers to anything inside it except its
 *       published {@code api} package.</li>
 *   <li>No code outside a module names its tables. Each module's tables carry
 *       its prefix, so a table name says whose it is.</li>
 * </ul>
 */
class ModuleBoundaryTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java/com/yellow/trade");
    private static final Path MAPPER_XML = Path.of("src/main/resources/mapper");

    /** Each Sprint 10 module and the prefix its tables carry. */
    static final Map<String, String> MODULES = Map.of(
            "preferences", "pref_",
            "notifications", "notif_",
            "watchlists", "watch_",
            "portfolio", "pf_",
            "advice", "advice_",
            "strategy", "strat_");

    @Test
    @DisplayName("nothing outside a module uses its insides: only its api package crosses the boundary")
    void onlyTheApiCrosses() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            String owner = moduleOf(file);
            String source = withoutPackageLine(Files.readString(file, StandardCharsets.UTF_8));
            for (String module : MODULES.keySet()) {
                if (module.equals(owner)) {
                    continue;
                }
                Matcher reference = Pattern.compile("com\\.yellow\\.trade\\." + module + "\\.(?!api\\.)[\\w*]+")
                        .matcher(source);
                while (reference.find()) {
                    violations.add(MAIN_JAVA.relativize(file) + " uses " + reference.group());
                }
            }
        }
        assertThat("a module's insides used from outside it", violations, empty());
    }

    @Test
    @DisplayName("no module's tables are named outside it")
    void tablesStayWithTheirModule() throws IOException {
        List<String> violations = new ArrayList<>();
        List<Path> files = new ArrayList<>(javaFiles());
        if (Files.isDirectory(MAPPER_XML)) {
            try (Stream<Path> xml = Files.walk(MAPPER_XML)) {
                xml.filter(p -> p.toString().endsWith(".xml")).forEach(files::add);
            }
        }
        for (Path file : files) {
            String owner = moduleOf(file);
            String source = Files.readString(file, StandardCharsets.UTF_8).toLowerCase();
            MODULES.forEach((module, prefix) -> {
                if (module.equals(owner)) {
                    return;
                }
                Matcher table = Pattern.compile("\\b" + prefix + "[a-z_]+\\b").matcher(source);
                while (table.find()) {
                    violations.add(file + " names " + table.group() + ", a " + module + " table");
                }
            });
        }
        assertThat("a module's table named outside it", violations, empty());
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** The module a file belongs to, or null for platform code; a mapper XML belongs by its name. */
    private static String moduleOf(Path file) {
        Path relative = file.startsWith(MAIN_JAVA) ? MAIN_JAVA.relativize(file) : file.getFileName();
        String first = relative.getName(0).toString();
        if (MODULES.containsKey(first)) {
            return first;
        }
        String name = file.getFileName().toString().toLowerCase();
        return MODULES.keySet().stream().filter(name::startsWith).findFirst().orElse(null);
    }

    private static String withoutPackageLine(String source) {
        return source.replaceFirst("(?m)^package [\\w.]+;", "");
    }
}
