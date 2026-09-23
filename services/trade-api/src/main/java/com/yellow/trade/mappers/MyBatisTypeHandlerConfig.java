package com.yellow.trade.mappers;

import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

// * MyBatis wiring: where the mapper interfaces are found, and the handler
// * for a type MyBatis does not know.

@Configuration
@MapperScan("com.yellow.trade.mappers")
public class MyBatisTypeHandlerConfig {

    @Bean
    public ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration ->
                configuration.getTypeHandlerRegistry().register(UUID.class, new UuidTypeHandler());
    }
}
