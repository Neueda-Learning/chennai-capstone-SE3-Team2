package com.yellow.trade;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The repository-root .env, read when the service is started from its main
 * method outside a container -- IntelliJ on Windows, or mvn spring-boot:run.
 * One file holds every service's settings, so nothing is typed into a run
 * configuration.
 *
 * Set as a default property, so it applies only to the process main() starts:
 * the tests never call main() and never read a developer's .env. In a
 * container there is no such file, and the environment compose sets wins over
 * it anyway: environment variables outrank imported files.
 */
final class LocalEnvFile {

    /** The working directory is the repository root, or this module's folder. */
    static final String[] SEARCHED = {".env", "../../.env"};

    private LocalEnvFile() {
    }

    /** spring.config.import for the given files, each optional and read as KEY=value lines. */
    static Map<String, Object> importing(String... paths) {
        return Map.of("spring.config.import", Arrays.stream(paths)
                .map(path -> "optional:file:" + path + "[.properties]")
                .collect(Collectors.joining(",")));
    }
}
