package com.yellow.trade.kyc;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduling was off in the Trade REST API until the KYC job needed it. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(KycProperties.class)
class KycSchedulingConfig {
}
