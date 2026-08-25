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
            @Value("${meridian.notification.smtp.start-tls-enabled:false}") boolean startTlsEnabled
    ) {
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
        return sender;
    }
}
