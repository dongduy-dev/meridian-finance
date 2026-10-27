package com.meridian.platform.approval.application.mapper;

import com.meridian.platform.approval.application.dto.*;
import com.meridian.platform.approval.application.event.*;
import com.meridian.platform.approval.domain.model.*;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalEventFactsTest {
    private static final UUID ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ITEM = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID VERSION = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 27, 12, 0, 0, 123456000);
    private final ApprovalMapper mapper = new ApprovalMapper();
    private final BusinessOperationContext operation = BusinessOperationContext.user(ID, ID, TIME);

    @ParameterizedTest
    @EnumSource(CorrectionReasonCode.class)
    void bothEventEnvelopesKeepExactCodesAndImmutableOrderedFacts(CorrectionReasonCode reason) {
        var first = task(CorrectionScope.DOCUMENT_REPLACEMENT, CorrectionResponsibility.CUSTOMER,
                CorrectionDocumentType.BANK_STATEMENT, "  Replace\nexactly  ", null);
        var last = task(CorrectionScope.DOCUMENT_REVIEW, CorrectionResponsibility.STAFF,
                CorrectionDocumentType.COLLATERAL_OWNERSHIP_EVIDENCE, null, "  Review\texactly  ");
        var source = new ArrayList<>(Arrays.asList(first, null, last));
        var request = new CorrectionPlanRequest(source);
        var recommendation = mapper.toRecordedEvent(recommendation(reason), operation, request);
        var decision = mapper.toRecordedEvent(decision(reason), VERSION, operation, request);
        source.clear();

        assertEquals(reason.name(), recommendation.reasonCode());
        assertEquals(reason.name(), decision.reasonCode());
        assertEquals(recommendation.correctionPlan(), decision.correctionPlan());
        assertEquals(3, recommendation.correctionPlan().tasks().size());
        assertNull(recommendation.correctionPlan().tasks().get(1));
        assertEquals(new CorrectionTaskFacts("DOCUMENT_REPLACEMENT", "CUSTOMER", "BANK_STATEMENT", false,
                ITEM, VERSION, "  Replace\nexactly  ", null), recommendation.correctionPlan().tasks().getFirst());
        assertEquals("  Review\texactly  ", recommendation.correctionPlan().tasks().getLast().staffInstruction());
        assertThrows(UnsupportedOperationException.class, () -> recommendation.correctionPlan().tasks().clear());
        assertThrows(UnsupportedOperationException.class, () -> decision.correctionPlan().tasks().set(0, null));
        assertEquals(ID, recommendation.recommendationId());
        assertEquals(ID, recommendation.loanApplicationId());
        assertEquals(VERSION, recommendation.reviewCycleId());
        assertEquals(ID, recommendation.loanOfficerUserId());
        assertEquals(TIME, recommendation.recordedAt());
        assertEquals(ID, decision.decisionId());
        assertEquals(ID, decision.loanApplicationId());
        assertEquals(VERSION, decision.reviewCycleId());
        assertEquals(ITEM, decision.reviewRecommendationId());
        assertEquals(ID, decision.approverUserId());
        assertEquals(TIME, decision.decidedAt());
        assertSame(operation, recommendation.operationContext());
        assertSame(operation, decision.operationContext());
        assertEquals(ReviewRecommendationEventAction.RETURN_TO_CUSTOMER_REVISION, recommendation.action());
        assertEquals(ApprovalDecisionEventAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION, decision.action());
    }

    @Test
    void mapperPreservesNullPlansListsCodesAndTaskFieldsWithoutValidation() {
        assertNull(mapper.toRecordedEvent(recommendation(null), operation, null).reasonCode());
        assertNull(mapper.toRecordedEvent(decision(null), VERSION, operation, null).correctionPlan());
        assertNull(mapper.toRecordedEvent(recommendation(null), operation, new CorrectionPlanRequest(null)).correctionPlan().tasks());
        assertNull(mapper.toRecordedEvent(decision(null), VERSION, operation, new CorrectionPlanRequest(null)).correctionPlan().tasks());
        var malformed = new CorrectionTaskRequest(null, null, null, false, null, null, null, null);
        var facts = mapper.toRecordedEvent(recommendation(null), operation,
                new CorrectionPlanRequest(List.of(malformed))).correctionPlan().tasks().getFirst();
        assertEquals(new CorrectionTaskFacts(null, null, null, false, null, null, null, null), facts);
    }

    @Test
    void factsConstructorAlsoCopiesItsCallerListAndPreservesNullElements() {
        var list = new ArrayList<CorrectionTaskFacts>();
        list.add(null);
        var facts = new CorrectionPlanFacts(list);
        list.clear();
        assertEquals(Arrays.asList((CorrectionTaskFacts) null), facts.tasks());
        assertThrows(UnsupportedOperationException.class, () -> facts.tasks().add(null));
        assertNull(new CorrectionPlanFacts(null).tasks());
    }

    @Test
    void publishedPlanJsonEqualsTheBaselineRequestShape() {
        var json = JsonMapper.builder().build();
        var request = new CorrectionPlanRequest(List.of(task(CorrectionScope.DOCUMENT_REPLACEMENT,
                CorrectionResponsibility.CUSTOMER, CorrectionDocumentType.RECENT_PAYSLIP, "  Replace  ", null)));
        var event = mapper.toRecordedEvent(recommendation(CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED), operation, request);
        assertEquals(json.valueToTree(request), json.valueToTree(event.correctionPlan()));
    }

    private CorrectionTaskRequest task(CorrectionScope scope, CorrectionResponsibility party,
                                       CorrectionDocumentType type, String customer, String staff) {
        return new CorrectionTaskRequest(scope, party, type, false, ITEM, VERSION, customer, staff);
    }
    private ReviewRecommendation recommendation(CorrectionReasonCode reason) {
        return new ReviewRecommendation(ID, ID, VERSION, ID, ReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION,
                null, reason, "excluded private note", TIME);
    }
    private ApprovalDecision decision(CorrectionReasonCode reason) {
        return new ApprovalDecision(ID, ID, ITEM, ID, ApprovalDecisionAction.REQUEST_CUSTOMER_OR_STAFF_CORRECTION,
                null, reason, "excluded private note", TIME);
    }
}
