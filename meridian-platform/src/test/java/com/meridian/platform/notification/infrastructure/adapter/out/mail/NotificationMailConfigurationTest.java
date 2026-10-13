package com.meridian.platform.notification.infrastructure.adapter.out.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMailConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationMailConfiguration.class);

    private ApplicationContextRunner production() {
        return runner.withPropertyValues(
                "meridian.notification.smtp.production=true",
                "meridian.notification.smtp.host=smtp.resend.com", "meridian.notification.smtp.port=587",
                "meridian.notification.smtp.username=resend", "meridian.notification.smtp.password=synthetic-credential",
                "meridian.notification.smtp.authentication-enabled=true", "meridian.notification.smtp.start-tls-enabled=true",
                "meridian.notification.smtp.start-tls-required=true",
                "meridian.notification.from-address=notifications@meridianfinance.dev",
                "meridian.frontend.base-url=https://meridianfinance.dev",
                "meridian.internal-frontend.base-url=https://internal.meridianfinance.dev");
    }

    @Test
    void productionRequiresTlsAuthenticationIdentityChecksAndBoundedTimeouts() {
        production().run(context -> {
            var sender = context.getBean(JavaMailSenderImpl.class);
            assertThat(sender.getHost()).isEqualTo("smtp.resend.com");
            assertThat(sender.getPort()).isEqualTo(587);
            assertThat(sender.getUsername()).isEqualTo("resend");
            var props = sender.getJavaMailProperties();
            for (String key : new String[]{"auth", "starttls.enable", "starttls.required", "ssl.checkserveridentity"}) {
                assertThat(props.getProperty("mail.smtp." + key)).isEqualTo("true");
            }
            for (String key : new String[]{"connectiontimeout", "timeout", "writetimeout"}) {
                assertThat(props.getProperty("mail.smtp." + key)).isEqualTo("5000");
            }
            assertThat(props.getProperty("mail.debug")).isEqualTo("false");
            assertThat(props).doesNotContainKey("mail.smtp.ssl.trust");
        });
    }

    @Test
    void productionRejectsMissingMalformedOrWeakenedConfigurationWithoutEchoingValues() {
        for (String setting : new String[]{"password=", "password=REPLACE_WITH_PRIVATE_KEY", "password=bad credential",
                "host=localhost", "port=1025", "username=wrong", "authentication-enabled=false",
                "start-tls-enabled=false", "start-tls-required=false", "connection-timeout-ms=0",
                "read-timeout-ms=-1", "write-timeout-ms=30001"}) {
            production().withPropertyValues("meridian.notification.smtp." + setting).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure().getCause().getMessage()).doesNotContain("synthetic-credential", "bad credential");
            });
        }
        production().withPropertyValues("meridian.frontend.base-url=http://localhost:5173").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void localMailpitPathRetainsUnauthenticatedDevelopmentDefaults() {
        runner.withPropertyValues("meridian.notification.smtp.host=mailpit").run(context -> {
            var sender = context.getBean(JavaMailSenderImpl.class);
            assertThat(sender.getHost()).isEqualTo("mailpit");
            assertThat(sender.getPort()).isEqualTo(1025);
            assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.starttls.required")).isEqualTo("false");
            assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("false");
        });
    }
}
