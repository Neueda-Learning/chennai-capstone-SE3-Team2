package com.yellow.trade.payments;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Scheduling itself is switched on once, in the KYC module. */
@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
class PaymentConfig {
}
