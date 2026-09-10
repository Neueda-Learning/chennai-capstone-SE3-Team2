package com.yellow.trade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Trade REST API.
 *
 * Component scanning starts at com.yellow.trade, which is the transport layer.
 * The domain beside it, under com.yellow.{dto,entities,enums,exceptions,
 * repositories,services}, is deliberately outside that scan: nothing in it
 * carries a Spring annotation, and the one bean it contributes is declared by
 * hand in DomainConfig.
 *
 * @MapperScan is deliberately NOT here. On this class it would register a
 * mapper bean into every @WebMvcTest slice, each of which then demands a
 * DataSource the slice has no reason to own -- so the web-layer tests could
 * only run with a database. It lives on MapperScanConfig instead, which a
 * slice does not load.
 */
@SpringBootApplication
public class TradeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeApiApplication.class, args);
    }
}
