package com.yellow.trade.marketdata;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MarketDataProperties.class)
public class MarketDataConfig {
}
