package com.meridian.platform.notification.infrastructure.adapter.out.mail;

import com.meridian.platform.notification.application.port.out.EmailSenderPort;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class JavaMailEmailSenderAdapter implements EmailSenderPort {

    private final JavaMailSender javaMailSender;

    public JavaMailEmailSenderAdapter(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Override
    public void send(String fromAddress, String recipientAddress, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipientAddress);
        message.setSubject(subject);
        message.setText(body);
        javaMailSender.send(message);
    }
}
