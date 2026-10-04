package com.yellow.trade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TradeApiApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(TradeApiApplication.class);
        app.setDefaultProperties(LocalEnvFile.importing(LocalEnvFile.SEARCHED));
        app.run(args);
    }
}
