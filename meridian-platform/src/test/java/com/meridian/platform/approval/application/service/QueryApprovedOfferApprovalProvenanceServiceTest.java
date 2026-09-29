package com.meridian.platform.approval.application.service;

import com.meridian.platform.approval.application.port.out.ApprovalDecisionRepository;
import com.meridian.platform.approval.domain.model.ApprovalDecision;
import com.meridian.platform.approval.domain.model.ApprovalDecisionAction;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryApprovedOfferApprovalProvenanceServiceTest {
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final LocalDateTime OFFER_GENERATED_AT = LocalDateTime.of(2026, 9, 15, 9, 0);

    @Test
    void selectsOnlyExactApproveForReferencedOfferInsteadOfLatestDecision() {
        ApprovalDecisionRepository repository = mock(ApprovalDecisionRepository.class);
        UUID approver = UUID.randomUUID();
        when(repository.findByLoanApplicationIdOrderByDecidedAtDesc(APPLICATION_ID)).thenReturn(List.of(
                decision(ApprovalDecisionAction.REJECT, OFFER_GENERATED_AT.plusDays(1), UUID.randomUUID()),
                decision(ApprovalDecisionAction.APPROVE, OFFER_GENERATED_AT.plusHours(1), UUID.randomUUID()),
                decision(ApprovalDecisionAction.APPROVE, OFFER_GENERATED_AT, approver)
        ));

        var result = new QueryApprovedOfferApprovalProvenanceService(repository)
                .requireExactApproval(APPLICATION_ID, OFFER_GENERATED_AT);
        assertEquals(approver, result.approverUserId());
        assertEquals(OFFER_GENERATED_AT, result.approvedAt());
    }

    @Test
    void zeroOrAmbiguousExactApprovalsFailClosed() {
        ApprovalDecisionRepository repository = mock(ApprovalDecisionRepository.class);
        var service = new QueryApprovedOfferApprovalProvenanceService(repository);
        when(repository.findByLoanApplicationIdOrderByDecidedAtDesc(APPLICATION_ID)).thenReturn(List.of(
                decision(ApprovalDecisionAction.APPROVE, OFFER_GENERATED_AT.plusSeconds(1), UUID.randomUUID())
        ));
        assertConflict(() -> service.requireExactApproval(APPLICATION_ID, OFFER_GENERATED_AT));
        when(repository.findByLoanApplicationIdOrderByDecidedAtDesc(APPLICATION_ID)).thenReturn(List.of(
                decision(ApprovalDecisionAction.APPROVE, OFFER_GENERATED_AT, UUID.randomUUID()),
                decision(ApprovalDecisionAction.APPROVE, OFFER_GENERATED_AT, UUID.randomUUID())
        ));
        assertConflict(() -> service.requireExactApproval(APPLICATION_ID, OFFER_GENERATED_AT));
    }

    private static ApprovalDecision decision(ApprovalDecisionAction action, LocalDateTime at, UUID approver) {
        return new ApprovalDecision(UUID.randomUUID(), APPLICATION_ID, UUID.randomUUID(), approver,
                action, null, null, null, at);
    }

    private static void assertConflict(org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(BusinessStateConflictException.class, operation);
        assertEquals("SYSTEM_STATE_CONFLICT", failure.getErrorCode());
    }
}
