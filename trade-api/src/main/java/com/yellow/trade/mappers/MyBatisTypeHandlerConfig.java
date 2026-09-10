package com.yellow.trade.mappers;

import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * MyBatis wiring: where the mapper interfaces are found, and the handler
 * for a type MyBatis does not know.
 *
 * @MapperScan sits here rather than on the application class so that a
 * @WebMvcTest slice does not inherit mapper beans it cannot satisfy.
 *
 * Registers the UUID handler on the MyBatis configuration.
 *
 * Done here rather than with mybatis.type-handlers-package because that
 * property relies on scanning a package for classes, and classpath scanning
 * inside a Spring Boot fat jar is exactly where that quietly finds nothing.
 * The failure mode is not subtle -- the application refuses to start with
 * "No typehandler found for property orderId" -- but it only appears once the
 * jar is built, which is to say in the container and not on a laptop.
 *
 * Registering the instance directly cannot miss.
 */
@Configuration
@MapperScan("com.yellow.trade.mappers")
public class MyBatisTypeHandlerConfig {

    @Bean
    public ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration ->
                configuration.getTypeHandlerRegistry().register(UUID.class, new UuidTypeHandler());
    }
}
