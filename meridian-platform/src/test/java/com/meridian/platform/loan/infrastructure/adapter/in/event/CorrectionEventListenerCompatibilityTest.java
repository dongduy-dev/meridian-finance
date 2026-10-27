package com.meridian.platform.loan.infrastructure.adapter.in.event;

import com.meridian.platform.approval.application.event.*;
import com.meridian.platform.loan.application.dto.ApplyApprovalDecisionCommand;
import com.meridian.platform.loan.application.dto.ApplyReviewRecommendationCommand;
import com.meridian.platform.loan.application.port.in.ApplyApprovalDecisionUseCase;
import com.meridian.platform.loan.application.port.in.ApplyReviewRecommendationUseCase;
import com.meridian.platform.loan.domain.model.LoanCorrectionReasonCode;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrectionEventListenerCompatibilityTest {
    private final UUID id = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID version = UUID.randomUUID();
    private final LocalDateTime time = LocalDateTime.of(2026, 10, 27, 12, 0);
    private final BusinessOperationContext operation = BusinessOperationContext.user(id, id, time);
    private final CorrectionPlanFacts plan = new CorrectionPlanFacts(Arrays.asList(
            new CorrectionTaskFacts("DOCUMENT_REPLACEMENT", "CUSTOMER", "BANK_STATEMENT", false,
                    item, version, "  Replace\n  ", null), null,
            new CorrectionTaskFacts("DOCUMENT_REVIEW", "STAFF", "BANK_STATEMENT", false,
                    item, version, null, "  Review\t  ")));

    @ParameterizedTest
    @EnumSource(ApprovalDecisionEventAction.class)
    void decisionActionAndCorrectionFactsMapExactly(ApprovalDecisionEventAction action) {
        var useCase = mock(ApplyApprovalDecisionUseCase.class);
        new ApprovalDecisionEventListener(useCase).onApprovalDecisionRecorded(decision(action));
        var capture = ArgumentCaptor.forClass(ApplyApprovalDecisionCommand.class);
        verify(useCase).applyApprovalDecision(capture.capture());
        var command = capture.getValue();
        assertEquals(action.name(), command.action().name());
        assertEquals(LoanCorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED, command.reasonCode());
        assertEquals(item, command.reviewCycleId());
        assertEquals(version, command.reviewRecommendationId());
        assertEquals(LoanCorrectionEventMapper.toPlan(plan), command.correctionPlan());
        assertNull(command.correctionPlan().tasks().get(1));
        assertEquals("  Replace\n  ", command.correctionPlan().tasks().getFirst().customerInstruction());
    }

    @ParameterizedTest
    @EnumSource(ReviewRecommendationEventAction.class)
    void recommendationActionAndCorrectionFactsMapExactly(ReviewRecommendationEventAction action) {
        var useCase = mock(ApplyReviewRecommendationUseCase.class);
        new ReviewRecommendationEventListener(useCase).onReviewRecommendationRecorded(recommendation(action));
        var capture = ArgumentCaptor.forClass(ApplyReviewRecommendationCommand.class);
        verify(useCase).applyReviewRecommendation(capture.capture());
        assertEquals(action.name(), capture.getValue().action().name());
        assertEquals(LoanCorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED, capture.getValue().reasonCode());
        assertEquals(LoanCorrectionEventMapper.toPlan(plan), capture.getValue().correctionPlan());
        assertEquals("  Review\t  ", capture.getValue().correctionPlan().tasks().getLast().staffInstruction());
    }

    @Test
    void springEventPublicationPropagatesBothLoanFailuresSynchronously() throws Exception {
        var decisions = mock(ApplyApprovalDecisionUseCase.class);
        var recommendations = mock(ApplyReviewRecommendationUseCase.class);
        var failure = new IllegalStateException("mandatory Loan failure");
        doThrow(failure).when(decisions).applyApprovalDecision(any());
        doThrow(failure).when(recommendations).applyReviewRecommendation(any());
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ApplyApprovalDecisionUseCase.class, () -> decisions);
            context.registerBean(ApplyReviewRecommendationUseCase.class, () -> recommendations);
            context.register(ApprovalDecisionEventListener.class, ReviewRecommendationEventListener.class);
            context.refresh();
            assertSame(failure, assertThrows(IllegalStateException.class, () -> context.publishEvent(decision(ApprovalDecisionEventAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION))));
            assertSame(failure, assertThrows(IllegalStateException.class, () -> context.publishEvent(recommendation(ReviewRecommendationEventAction.RETURN_TO_CUSTOMER_REVISION))));
        }
        assertNotNull(ApprovalDecisionEventListener.class.getMethod("onApprovalDecisionRecorded", ApprovalDecisionRecordedEvent.class).getAnnotation(EventListener.class));
        assertNotNull(ReviewRecommendationEventListener.class.getMethod("onReviewRecommendationRecorded", ReviewRecommendationRecordedEvent.class).getAnnotation(EventListener.class));
    }

    @Test
    void nullPlansListsAndTaskFieldsReachLoanWithoutPrematureValidation() {
        assertNull(LoanCorrectionEventMapper.toPlan(null));
        assertNull(LoanCorrectionEventMapper.toPlan(new CorrectionPlanFacts(null)).tasks());
        var malformed = new CorrectionTaskFacts(null, null, null, false, null, null, null, null);
        assertNull(LoanCorrectionEventMapper.toPlan(new CorrectionPlanFacts(java.util.List.of(malformed))).tasks().getFirst().scope());
    }

    private ApprovalDecisionRecordedEvent decision(ApprovalDecisionEventAction action) {
        return new ApprovalDecisionRecordedEvent(id, id, item, version, id, action, null,
                "DOCUMENT_REPLACEMENT_REQUIRED", plan, time, operation);
    }
    private ReviewRecommendationRecordedEvent recommendation(ReviewRecommendationEventAction action) {
        return new ReviewRecommendationRecordedEvent(id, id, item, id, action, null,
                "DOCUMENT_REPLACEMENT_REQUIRED", plan, time, operation);
    }
}
