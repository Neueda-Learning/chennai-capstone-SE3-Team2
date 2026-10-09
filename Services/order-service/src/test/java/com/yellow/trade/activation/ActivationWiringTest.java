package com.yellow.trade.activation;

import com.yellow.trade.mappers.ActivationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The activation package wired by Spring, without a database or a broker: the
 * properties bind, every bean is created, and the listener is registered under
 * its own group. The DB-gated integration tests are the only other thing that
 * loads this, and they skip without Docker.
 */
class ActivationWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    ValidationAutoConfiguration.class,
                    RestClientAutoConfiguration.class,
                    MailSenderAutoConfiguration.class,
                    KafkaAutoConfiguration.class))
            .withUserConfiguration(ActivationKafkaConfig.class, ActivationService.class,
                    AccountProvisionedListener.class, AuthTokenClient.class, SmtpActivationMailSender.class)
            .withBean(ActivationMapper.class, () -> mock(ActivationMapper.class))
            .withPropertyValues(
                    "activation.topic=account-provisioning",
                    "activation.consumer-group=activation-mailer",
                    "activation.auth-base-url=http://auth:3000",
                    "activation.link-base-url=http://localhost:3000/activate",
                    "activation.consumer.auto-startup=false",
                    "spring.mail.host=smtp.gmail.com",
                    "spring.mail.username=noreply@example.invalid",
                    "spring.mail.password=not-a-real-password");

    @Test
    @DisplayName("with its settings present, the package starts and registers its listener in its own group")
    void wiresUp() {
        runner.withPropertyValues(
                        "activation.internal-secret=an-internal-test-secret-of-32-plus-bytes",
                        "activation.mail-from=noreply@example.invalid")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ActivationService.class);
                    assertThat(context).hasSingleBean(ActivationMailSender.class);

                    MessageListenerContainer container =
                            context.getBean(KafkaListenerEndpointRegistry.class).getListenerContainer("activation-mailer");
                    assertThat(container).isNotNull();
                    assertThat(container.getGroupId()).isEqualTo("activation-mailer");
                    assertThat(container.getContainerProperties().getTopics()).containsExactly("account-provisioning");
                    assertThat(container.isRunning()).isFalse();
                });
    }

    @Test
    @DisplayName("without the internal secret, it refuses to start, naming the setting")
    void refusesWithoutTheSecret() {
        runner.withPropertyValues("activation.mail-from=noreply@example.invalid")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("internalSecret"));
    }

    @Test
    @DisplayName("the properties never print the secret")
    void secretNotInToString() {
        ActivationProperties properties = new ActivationProperties("t", "g", "http://a", "s3cr3t-value", "http://l", "f@x");
        assertThat(properties.toString()).doesNotContain("s3cr3t-value");
    }
}
