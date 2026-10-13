package com.meridian.platform.notification.infrastructure.adapter.out.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration(proxyBeanMethods = false)
public class NotificationMailConfiguration {

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    JavaMailSender javaMailSender(
            @Value("${meridian.notification.smtp.host:localhost}") String host,
            @Value("${meridian.notification.smtp.port:1025}") int port,
            @Value("${meridian.notification.smtp.username:}") String username,
            @Value("${meridian.notification.smtp.password:}") String password,
            @Value("${meridian.notification.smtp.authentication-enabled:false}") boolean authenticationEnabled,
            @Value("${meridian.notification.smtp.start-tls-enabled:false}") boolean startTlsEnabled,
            @Value("${meridian.notification.smtp.start-tls-required:false}") boolean startTlsRequired,
            @Value("${meridian.notification.smtp.connection-timeout-ms:5000}") int connectionTimeout,
            @Value("${meridian.notification.smtp.read-timeout-ms:5000}") int readTimeout,
            @Value("${meridian.notification.smtp.write-timeout-ms:5000}") int writeTimeout,
            @Value("${meridian.notification.smtp.production:false}") boolean production,
            @Value("${meridian.notification.from-address:no-reply@meridian.local}") String fromAddress,
            @Value("${meridian.frontend.base-url:http://localhost:5173}") String customerUrl,
            @Value("${meridian.internal-frontend.base-url:http://localhost:5174}") String internalUrl
    ) {
        if (host.isBlank() || !host.matches("[A-Za-z0-9.-]+") || port < 1 || port > 65535
                || !bounded(connectionTimeout) || !bounded(readTimeout) || !bounded(writeTimeout)
                || (authenticationEnabled && (username.isBlank() || password.isBlank()))
                || (startTlsRequired && !startTlsEnabled)) {
            throw new IllegalArgumentException("Invalid SMTP configuration; check required settings privately.");
        }
        if (production && (!"smtp.resend.com".equals(host) || port != 587 || !"resend".equals(username)
                || password.isBlank() || password.chars().anyMatch(Character::isWhitespace)
                || password.startsWith("REPLACE_") || !authenticationEnabled || !startTlsRequired
                || !"notifications@meridianfinance.dev".equals(fromAddress)
                || !"https://meridianfinance.dev".equals(customerUrl)
                || !"https://internal.meridianfinance.dev".equals(internalUrl))) {
            throw new IllegalArgumentException("Production SMTP requires the approved authenticated TLS configuration.");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (!username.isBlank()) {
            sender.setUsername(username);
        }
        if (!password.isBlank()) {
            sender.setPassword(password);
        }
        sender.getJavaMailProperties().setProperty("mail.smtp.auth", Boolean.toString(authenticationEnabled));
        sender.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", Boolean.toString(startTlsEnabled));
        sender.getJavaMailProperties().setProperty("mail.smtp.starttls.required", Boolean.toString(startTlsRequired));
        sender.getJavaMailProperties().setProperty("mail.smtp.ssl.checkserveridentity", "true");
        sender.getJavaMailProperties().setProperty("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", Integer.toString(connectionTimeout));
        sender.getJavaMailProperties().setProperty("mail.smtp.timeout", Integer.toString(readTimeout));
        sender.getJavaMailProperties().setProperty("mail.smtp.writetimeout", Integer.toString(writeTimeout));
        sender.getJavaMailProperties().setProperty("mail.debug", "false");
        return sender;
    }

    private static boolean bounded(int timeout) {
        return timeout > 0 && timeout <= 30000;
    }
}
