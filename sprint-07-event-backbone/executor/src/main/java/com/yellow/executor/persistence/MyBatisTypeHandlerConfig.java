package com.yellow.executor.persistence;

import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Registers {@link UuidTypeHandler} with MyBatis so that Postgres's native
 * {@code uuid} columns map cleanly to {@link UUID} on both read and write.
 *
 * <p>Without this, MyBatis's default UUID handler treats the column as VARCHAR
 * and either fails on write (Postgres refuses the cast) or leaves the read as
 * a String -- which auto-mapping into an {@code ExecutableOrderRow} silently
 * drops, leaving {@code orderId} null and the guarded {@code UPDATE} matching
 * zero rows. The visible symptom is "duplicate delivery ignored: order null".
 *
 * <p>Same shape as trade-api's {@code MyBatisTypeHandlerConfig}. Two Spring
 * Boot processes, two MyBatis registries, one handler each.
 */
@Configuration
public class MyBatisTypeHandlerConfig {

    @Bean
    public ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration ->
                configuration.getTypeHandlerRegistry().register(UUID.class, new UuidTypeHandler());
    }
}
