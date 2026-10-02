package com.meridian.platform.loan.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccountingCaseContextDto(CustomerDto customer, HandoffDto handoff) {
    @Override
    public String toString() {
        return "AccountingCaseContextDto[customer=redacted, handoff=redacted]";
    }

    public record CustomerDto(String customerNumber, String fullName, String phoneNumber) {
        @Override
        public String toString() {
            return "CustomerDto[identity=redacted]";
        }
    }

    public record StaffActorDto(UUID userId, String displayName, String email) {
        @Override
        public String toString() {
            return "StaffActorDto[userId=" + userId + ", displayIdentity=redacted]";
        }
    }

    public record ActorEventDto(StaffActorDto actor, LocalDateTime at) {
    }

    public record AcknowledgmentDto(
            String mode, StaffActorDto recordedBy, LocalDateTime at, UUID evidenceDocumentVersionId
    ) {
    }

    public record HandoffDto(
            ActorEventDto approved,
            ActorEventDto contractPrepared,
            AcknowledgmentDto customerAcknowledgment,
            ActorEventDto readinessConfirmed,
            ActorEventDto disbursementConfirmed
    ) {
    }
}
