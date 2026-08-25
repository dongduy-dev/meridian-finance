package com.meridian.platform.notification.application.port.out;

public interface EmailSenderPort {

    void send(String fromAddress, String recipientAddress, String subject, String body);
}
