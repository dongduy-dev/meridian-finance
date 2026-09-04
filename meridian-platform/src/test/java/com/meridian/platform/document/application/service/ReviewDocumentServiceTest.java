package com.meridian.platform.document.application.service;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.document.application.dto.ReviewDocumentCommand;
import com.meridian.platform.document.application.port.out.DocumentChecklistRepository;
import com.meridian.platform.document.application.port.out.DocumentRepository;
import com.meridian.platform.document.application.port.out.LoanDocumentReviewCorrectionPort;
import com.meridian.platform.document.application.port.out.LoanDocumentWorkflowPort;
import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import com.meridian.platform.document.domain.model.DocumentRequirementStatus;
import com.meridian.platform.document.domain.model.DocumentReviewDecision;
import com.meridian.platform.document.domain.model.DocumentReviewOutcome;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.document.domain.model.StoredDocument;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewDocumentServiceTest {
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID CHECKLIST_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 9, 30);

    @Mock LoanDocumentWorkflowPort workflowPort;
    @Mock DocumentChecklistRepository checklistRepository;
    @Mock DocumentRepository documentRepository;
    @Mock LoanDocumentReviewCorrectionPort correctionPort;
    @Mock BusinessAuditPublisher auditPublisher;
    @Mock CurrentUserProvider currentUserProvider;
    private ReviewDocumentService service;

    @BeforeEach
    void setUp() {
        service = new ReviewDocumentService(
                workflowPort, checklistRepository, documentRepository, correctionPort,
                auditPublisher, currentUserProvider,
                Clock.fixed(Instant.parse("2026-09-04T09:30:00Z"), ZoneOffset.UTC)
        );
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                REVIEWER_ID, "reviewer@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), Set.of("document:review")
        ));
        when(workflowPort.lock(APPLICATION_ID)).thenReturn(
                new LoanDocumentWorkflowPort.LoanDocumentWorkflowSnapshot(
                        APPLICATION_ID, CUSTOMER_ID, LoanApplicationStatus.UNDER_REVIEW
                )
        );
        when(checklistRepository.findByLoanApplicationIdAndStage(
                APPLICATION_ID, DocumentChecklistStage.SUBMISSION
        )).thenReturn(Optional.of(checklist(item(null))));
        when(documentRepository.findDocumentByChecklistItemIdForUpdate(ITEM_ID))
                .thenReturn(Optional.of(document()));
    }

    @Test
    void firstReviewSucceedsAndExactReplayReturnsTheOriginalDecision() {
        DocumentChecklistItem unreviewed = item(null);
        DocumentReviewCommandFixture fixture = new DocumentReviewCommandFixture(REQUEST_ID);
        DocumentReviewDecision saved = fixture.decision(UUID.randomUUID(), DocumentReviewOutcome.ACCEPT_DOCUMENT);
        when(checklistRepository.findItemByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(unreviewed));
        when(documentRepository.findReviewDecisionByReviewRequestId(REQUEST_ID))
                .thenReturn(Optional.empty(), Optional.of(saved));
        when(documentRepository.saveReviewDecision(any(DocumentReviewDecision.class))).thenReturn(saved);

        var first = service.review(fixture.accept());
        var replay = service.review(fixture.accept());

        assertEquals(saved.id(), first.reviewDecisionId());
        assertEquals(first, replay);
        verify(documentRepository).saveReviewDecision(any(DocumentReviewDecision.class));
        verify(checklistRepository).saveItem(any(DocumentChecklistItem.class));
        verify(auditPublisher).publish(any());
    }

    @Test
    void conflictingPayloadForTheSameRequestIdRemainsAnIdempotencyConflict() {
        DocumentReviewCommandFixture fixture = new DocumentReviewCommandFixture(REQUEST_ID);
        DocumentReviewDecision existing = fixture.decision(UUID.randomUUID(), DocumentReviewOutcome.ACCEPT_DOCUMENT);
        when(checklistRepository.findItemByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(item(existing.id())));
        when(documentRepository.findReviewDecisionByReviewRequestId(REQUEST_ID))
                .thenReturn(Optional.of(existing));

        BusinessStateConflictException error = assertThrows(
                BusinessStateConflictException.class,
                () -> service.review(fixture.replacement())
        );

        assertEquals("IDEMPOTENCY_KEY_REUSED", error.getErrorCode());
        verify(documentRepository, never()).saveReviewDecision(any());
    }

    @Test
    void rejectsANewLogicalReviewForAnAlreadyReviewedCurrentVersion() {
        UUID existingDecisionId = UUID.randomUUID();
        when(checklistRepository.findItemByIdForUpdate(ITEM_ID))
                .thenReturn(Optional.of(item(existingDecisionId)));
        when(documentRepository.findReviewDecisionByReviewRequestId(REQUEST_ID))
                .thenReturn(Optional.empty());

        BusinessStateConflictException error = assertThrows(
                BusinessStateConflictException.class,
                () -> service.review(new DocumentReviewCommandFixture(REQUEST_ID).accept())
        );

        assertEquals("DOCUMENT_ALREADY_REVIEWED", error.getErrorCode());
        assertEquals("Document version already reviewed.", error.getMessage());
        verify(documentRepository, never()).saveReviewDecision(any());
        verify(checklistRepository, never()).saveItem(any());
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void replacementDecisionCannotBeContradictedByANewAcceptanceForTheSameCurrentVersion() {
        UUID replacementDecisionId = UUID.randomUUID();
        when(checklistRepository.findItemByIdForUpdate(ITEM_ID))
                .thenReturn(Optional.of(item(replacementDecisionId)));
        when(documentRepository.findReviewDecisionByReviewRequestId(REQUEST_ID))
                .thenReturn(Optional.empty());

        BusinessStateConflictException error = assertThrows(
                BusinessStateConflictException.class,
                () -> service.review(new DocumentReviewCommandFixture(REQUEST_ID).accept())
        );

        assertEquals("DOCUMENT_ALREADY_REVIEWED", error.getErrorCode());
        verify(documentRepository, never()).saveReviewDecision(any());
        verify(correctionPort, never()).requestCustomerReplacement(
                any(), any(), any(), any(), any(), any()
        );
    }

    private static DocumentChecklist checklist(DocumentChecklistItem item) {
        return new DocumentChecklist(
                CHECKLIST_ID, APPLICATION_ID, DocumentChecklistStage.SUBMISSION,
                List.of(item), NOW.minusDays(1)
        );
    }

    private static DocumentChecklistItem item(UUID reviewDecisionId) {
        return new DocumentChecklistItem(
                ITEM_ID, CHECKLIST_ID, DocumentType.BANK_STATEMENT,
                DocumentRequirementStatus.REQUIRED, reviewDecisionId,
                NOW.minusDays(1), NOW.minusHours(1)
        );
    }

    private static StoredDocument document() {
        return new StoredDocument(
                DOCUMENT_ID, ITEM_ID, VERSION_ID, NOW.minusDays(1), NOW.minusHours(1)
        );
    }

    private record DocumentReviewCommandFixture(UUID requestId) {
        ReviewDocumentCommand accept() {
            return new ReviewDocumentCommand(
                    APPLICATION_ID, ITEM_ID, VERSION_ID, requestId,
                    DocumentReviewOutcome.ACCEPT_DOCUMENT, null, null,
                    REVIEWER_ID, false
            );
        }

        ReviewDocumentCommand replacement() {
            return new ReviewDocumentCommand(
                    APPLICATION_ID, ITEM_ID, VERSION_ID, requestId,
                    DocumentReviewOutcome.REQUEST_REPLACEMENT, null, "restricted",
                    CorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED, "Replace the evidence.",
                    REVIEWER_ID, false
            );
        }

        DocumentReviewDecision decision(UUID id, DocumentReviewOutcome outcome) {
            return new DocumentReviewDecision(
                    id, ITEM_ID, VERSION_ID, requestId, outcome, null,
                    null, null, null, REVIEWER_ID, NOW
            );
        }
    }
}
