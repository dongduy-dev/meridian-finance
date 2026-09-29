package com.meridian.platform.identity.infrastructure.adapter.out.notification;

import com.meridian.platform.identity.application.port.out.StaffPasswordSetupNotificationPort;
import com.meridian.platform.notification.application.port.in.SendStaffPasswordSetupUseCase;
import com.meridian.platform.notification.application.port.in.StaffPasswordSetupMessage;
import org.springframework.stereotype.Component;

@Component
public class StaffPasswordSetupNotificationAdapter implements StaffPasswordSetupNotificationPort {
    private final SendStaffPasswordSetupUseCase sender;

    public StaffPasswordSetupNotificationAdapter(SendStaffPasswordSetupUseCase sender) {
        this.sender = sender;
    }

    @Override
    public void sendSetupEmail(String recipientEmail, String rawToken) {
        sender.send(new StaffPasswordSetupMessage(recipientEmail, rawToken));
    }
}
