package com.yellow.trade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/** The root .env reaches the service's settings, and a missing one is no error. */
class LocalEnvFileTest {

    @Configuration
    static class Nothing {
    }

    private static ConfigurableApplicationContext start(Path... envFiles) {
        SpringApplication app = new SpringApplication(Nothing.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(LocalEnvFile.importing(
                java.util.Arrays.stream(envFiles).map(Path::toString).toArray(String[]::new)));
        return app.run();
    }

    @Test
    @DisplayName("a KEY=value line in the .env becomes a setting the service can read")
    void readsTheFile(@TempDir Path dir) throws IOException {
        Path env = dir.resolve(".env");
        Files.writeString(env, "# a comment line\nDB_PASSWORD=from-the-env-file\nMF_NAV_API_KEY=\n");

        try (ConfigurableApplicationContext context = start(env)) {
            assertThat(context.getEnvironment().getProperty("DB_PASSWORD"), is("from-the-env-file"));
            assertThat(context.getEnvironment().getProperty("MF_NAV_API_KEY"), is(""));
        }
    }

    @Test
    @DisplayName("no .env is no error: in a container the environment is all there is")
    void missingFileIsFine(@TempDir Path dir) {
        try (ConfigurableApplicationContext context = start(dir.resolve(".env"))) {
            assertThat(context.getEnvironment().getProperty("DB_PASSWORD"), nullValue());
        }
    }
}
