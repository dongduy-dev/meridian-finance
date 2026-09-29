package com.meridian.platform.approval.application.port.in;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApprovedOfferApprovalProvenance(UUID approverUserId, LocalDateTime approvedAt) {
}
