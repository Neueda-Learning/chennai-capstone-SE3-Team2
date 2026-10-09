package com.yellow.executor.persistence;

import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Registers UuidTypeHandler with MyBatis so that Postgres's native uuid
 * columns map cleanly to UUID on both read and write.
 */
@Configuration
public class MyBatisTypeHandlerConfig {

    @Bean
    public ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration ->
                configuration.getTypeHandlerRegistry().register(UUID.class, new UuidTypeHandler());
    }
}
