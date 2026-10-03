package com.meridian.platform.customer.application.dto;

import com.meridian.platform.customer.application.port.out.CustomerIdentityEvidencePort;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerIdentityVerificationDto(
        UUID verificationId, int sequence, String customerNumber, String fullName,
        String source, String method, String status, String rejectionReason,
        LocalDateTime submittedAt, LocalDateTime completedAt, CustomerIdentityEvidencePort.Evidence evidence) {}
