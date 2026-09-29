package com.meridian.platform.loan.application.port.out;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApprovedOfferApprovalSnapshot(UUID approverUserId, LocalDateTime approvedAt) {
}
