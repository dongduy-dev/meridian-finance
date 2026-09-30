package com.meridian.platform.loan.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffLoanAccountServicingProvenanceDto(
        UUID loanApplicationId,
        UUID loanAccountId,
        ActorEvent originatingDisbursement,
        RepaymentPage repaymentHistory,
        List<StatusEvent> statusHistory,
        ActorEvent settlement,
        ActorEvent closure
) {
    public StaffLoanAccountServicingProvenanceDto {
        statusHistory = List.copyOf(statusHistory);
    }

    @Override
    public String toString() {
        return "StaffLoanAccountServicingProvenanceDto[loanApplicationId="
                + loanApplicationId + ", loanAccountId=" + loanAccountId
                + ", servicingAndActorEvidence=redacted]";
    }

    public record StaffActor(UUID userId, String displayName, String email) {
        @Override
        public String toString() {
            return "StaffActor[userId=" + userId + ", displayIdentity=redacted]";
        }
    }
    public record ServicingActor(String type, StaffActor staff) {}
    public record ActorEvent(ServicingActor actor, LocalDateTime at) {}
    public record StatusEvent(int sequenceNumber, String action, String fromStatus,
                              String toStatus, ServicingActor actor,
                              LocalDate servicingEvaluationDate, LocalDateTime occurredAt) {}
    public record RepaymentItem(RepaymentHistoryPageDto.ItemDto financial,
                                String transactionType, ServicingActor actor) {}
    public record RepaymentPage(int page, int size, long totalElements, int totalPages,
                                List<RepaymentItem> items) {
        public RepaymentPage { items = List.copyOf(items); }
    }
}
