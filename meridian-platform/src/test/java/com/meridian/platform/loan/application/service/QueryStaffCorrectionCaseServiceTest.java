package com.meridian.platform.loan.application.service;

import com.meridian.platform.approval.domain.model.CorrectionReasonCode;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationCancellationRepository;
import com.meridian.platform.loan.application.port.out.LoanAssistedActionEvidencePort;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryStaffCorrectionCaseServiceTest {
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID CREATOR_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 2, 11, 0);

    @Mock LoanApplicationRepository applications;
    @Mock LoanCorrectionRepository corrections;
    @Mock LoanApplicationCancellationRepository cancellations;
    @Mock LoanAssistedActionEvidencePort assistedEvidence;
    @Mock LoanDocumentChecklistPort documents;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock AssistedCustomerActionProvenanceComposer provenance;
    @Mock com.meridian.platform.loan.application.port.out.WorkflowActorDirectoryPort actors;
    @Mock com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository transitions;
    private QueryStaffCorrectionCaseService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(corrections.findLatestRequestByApplicationId(APPLICATION_ID))
                .thenAnswer(ignored -> corrections.findRequestsByApplicationId(APPLICATION_ID).stream().reduce((first, last) -> last));

        service = new QueryStaffCorrectionCaseService(
                applications, corrections, cancellations, assistedEvidence, documents,
                new CustomerCorrectionDocumentProof(documents), provenance, currentUserProvider, actors, transitions);
        when(currentUserProvider.currentUser()).thenReturn(staff(CREATOR_ID));
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application()));
    }

    @Test
    void returnsSafeEmptyStateWhenNoCorrectionExists() {
        when(corrections.findRequestsByApplicationId(APPLICATION_ID))
                .thenReturn(List.of());

        var result = service.query(APPLICATION_ID);

        assertNull(result.correctionRequest());
        assertEquals("MER-2026-000001", result.applicationNumber());
    }

    @Test
    void projectsMixedTasksProofAndCurrentActorMakerChecker() {
        LoanCorrectionRequest request = new LoanCorrectionRequest(
                REQUEST_ID, APPLICATION_ID, UUID.randomUUID(), "REQUEST_CORRECTION",
                CorrectionReasonCode.DOCUMENT_REVIEW_REQUIRED, CREATOR_ID,
                LoanCorrectionRequestStatus.OPEN, null, NOW.minusHours(3), null, null);
        LoanCorrectionTask customer = new LoanCorrectionTask(
                UUID.randomUUID(), REQUEST_ID, 1, LoanCorrectionResponsibility.CUSTOMER,
                LoanCorrectionScope.DOCUMENT_REPLACEMENT, DocumentType.BANK_STATEMENT,
                false, ITEM_ID, VERSION_ID, "Replace it.", null,
                LoanCorrectionTaskStatus.COMPLETED, UUID.randomUUID(), UUID.randomUUID(),
                NOW.minusHours(1), NOW.minusHours(2));
        LoanCorrectionTask staff = new LoanCorrectionTask(
                UUID.randomUUID(), REQUEST_ID, 2, LoanCorrectionResponsibility.STAFF,
                LoanCorrectionScope.DOCUMENT_REVIEW, DocumentType.BANK_STATEMENT,
                false, ITEM_ID, VERSION_ID, null, "Review replacement.",
                LoanCorrectionTaskStatus.OPEN, null, null, null, NOW.minusHours(2));
        UUID replacementId = UUID.randomUUID();
        when(corrections.findRequestsByApplicationId(APPLICATION_ID))
                .thenReturn(List.of(request));
        when(corrections.findTasksByRequestId(REQUEST_ID))
                .thenReturn(List.of(customer, staff));
        when(documents.requireCurrentVersion(APPLICATION_ID, ITEM_ID)).thenReturn(replacementId);
        when(documents.isVersionReviewed(APPLICATION_ID, ITEM_ID, replacementId)).thenReturn(true);

        var result = service.query(APPLICATION_ID).correctionRequest();

        assertEquals(true, result.makerCheckerBlockedForCurrentActor());
        assertEquals(List.of("NOT_APPLICABLE", "SATISFIED"), result.tasks().stream()
                .map(task -> task.proofState()).toList());
        assertNull(result.tasks().getFirst().staffInstruction());
        assertEquals("Review replacement.", result.tasks().get(1).staffInstruction());
    }

    @Test
    void reportsStaffOnlyReadyCorrectionAsStaffResubmittable() {
        assertStaffResubmissionReady(
                LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION,
                List.of(task(1, LoanCorrectionResponsibility.STAFF, LoanCorrectionTaskStatus.COMPLETED)),
                true
        );
    }

    @Test
    void reportsFullyCompletedMixedCorrectionAsStaffResubmittable() {
        assertStaffResubmissionReady(
                LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION,
                List.of(
                        task(1, LoanCorrectionResponsibility.CUSTOMER, LoanCorrectionTaskStatus.COMPLETED),
                        task(2, LoanCorrectionResponsibility.STAFF, LoanCorrectionTaskStatus.COMPLETED)
                ),
                true
        );
    }

    @Test
    void doesNotReportCustomerOnlyCorrectionAsStaffResubmittable() {
        assertStaffResubmissionReady(
                LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION,
                List.of(task(1, LoanCorrectionResponsibility.CUSTOMER, LoanCorrectionTaskStatus.COMPLETED)),
                false
        );
    }

    @Test
    void exposesAssistedCustomerTaskInstructionProofAndBackendActions() {
        when(applications.findById(APPLICATION_ID))
                .thenReturn(Optional.of(application(OriginationChannel.STAFF_ASSISTED)));
        when(currentUserProvider.currentUser()).thenReturn(staff(
                CREATOR_ID, Set.of("loan:correction:staff", "document:upload:assisted-correction")));
        LoanCorrectionRequest request = request(LoanCorrectionRequestStatus.OPEN);
        LoanCorrectionTask task = task(
                1, LoanCorrectionResponsibility.CUSTOMER, LoanCorrectionTaskStatus.OPEN);
        when(corrections.findRequestsByApplicationId(APPLICATION_ID))
                .thenReturn(List.of(request));
        when(corrections.findTasksByRequestId(REQUEST_ID)).thenReturn(List.of(task));
        when(documents.hasCurrentVersionDifferentFrom(ITEM_ID, VERSION_ID)).thenReturn(true);

        var result = service.query(APPLICATION_ID).correctionRequest();
        var projected = result.tasks().getFirst();

        assertEquals(false, result.makerCheckerBlockedForCurrentActor());
        assertEquals("Replace it.", projected.customerInstruction());
        assertNull(projected.staffInstruction());
        assertEquals("SATISFIED", projected.proofState());
        assertEquals(true, projected.customerSourceViaStaff());
        assertEquals(true, projected.uploadActionAvailable());
        assertEquals(true, projected.completionActionAvailable());
    }

    @Test
    void keepsCustomerDigitalCustomerTaskHiddenAndNonActionable() {
        LoanCorrectionRequest request = request(LoanCorrectionRequestStatus.OPEN);
        when(corrections.findRequestsByApplicationId(APPLICATION_ID))
                .thenReturn(List.of(request));
        when(corrections.findTasksByRequestId(REQUEST_ID)).thenReturn(List.of(task(
                1, LoanCorrectionResponsibility.CUSTOMER, LoanCorrectionTaskStatus.OPEN)));

        var projected = service.query(APPLICATION_ID).correctionRequest().tasks().getFirst();

        assertNull(projected.customerInstruction());
        assertEquals("NOT_APPLICABLE", projected.proofState());
        assertEquals(false, projected.customerSourceViaStaff());
        assertEquals(false, projected.uploadActionAvailable());
        assertEquals(false, projected.completionActionAvailable());
    }

    @Test
    void reportsCompletedAssistedCustomerOnlyCorrectionAsStaffResubmittable() {
        when(applications.findById(APPLICATION_ID))
                .thenReturn(Optional.of(application(OriginationChannel.STAFF_ASSISTED)));
        assertStaffResubmissionReady(
                LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION,
                List.of(task(1, LoanCorrectionResponsibility.CUSTOMER, LoanCorrectionTaskStatus.COMPLETED)),
                true
        );
    }

    @Test
    void projectsBackendDerivedAssistedCancellationAvailabilityAndEvidence() {
        when(applications.findById(APPLICATION_ID))
                .thenReturn(Optional.of(application(OriginationChannel.STAFF_ASSISTED)));
        when(currentUserProvider.currentUser()).thenReturn(staff(
                CREATOR_ID,
                Set.of(
                        "loan:correction:staff",
                        "loan:cancel:staff",
                        "document:upload:assisted-action"
                )));
        LoanCorrectionRequest request = request(LoanCorrectionRequestStatus.OPEN);
        when(corrections.findRequestsByApplicationId(APPLICATION_ID))
                .thenReturn(List.of(request));
        when(corrections.findTasksByRequestId(REQUEST_ID)).thenReturn(List.of(task(
                1, LoanCorrectionResponsibility.CUSTOMER, LoanCorrectionTaskStatus.OPEN)));
        UUID evidenceVersionId = UUID.randomUUID();
        when(assistedEvidence.findCancellationEvidence(APPLICATION_ID, REQUEST_ID))
                .thenReturn(Optional.of(new LoanAssistedActionEvidencePort.EvidenceSnapshot(
                        UUID.randomUUID(), evidenceVersionId, "CUSTOMER_CANCELLATION_REQUEST",
                        null, null, null, null, REQUEST_ID, 1,
                        "application/pdf", 100, NOW)));

        var cancellation = service.query(APPLICATION_ID).assistedCancellation();

        assertEquals(true, cancellation.available());
        assertEquals(REQUEST_ID, cancellation.correctionRequestId());
        assertEquals(evidenceVersionId, cancellation.evidence().documentVersionId());
        assertEquals(true, cancellation.evidenceUploadAvailable());
        assertEquals(true, cancellation.cancellationCommandAvailable());
    }

    @Test
    void keepsAssistedCancellationUnavailableForCustomerDigitalApplication() {
        when(corrections.findRequestsByApplicationId(APPLICATION_ID))
                .thenReturn(List.of(request(LoanCorrectionRequestStatus.OPEN)));
        when(corrections.findTasksByRequestId(REQUEST_ID)).thenReturn(List.of(task(
                1, LoanCorrectionResponsibility.CUSTOMER, LoanCorrectionTaskStatus.OPEN)));

        var cancellation = service.query(APPLICATION_ID).assistedCancellation();

        assertEquals(false, cancellation.available());
        assertNull(cancellation.correctionRequestId());
        assertNull(cancellation.evidence());
    }

    @Test
    void doesNotReportCorrectionWithIncompleteStaffTaskAsStaffResubmittable() {
        assertStaffResubmissionReady(
                LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION,
                List.of(task(1, LoanCorrectionResponsibility.STAFF, LoanCorrectionTaskStatus.OPEN)),
                false
        );
    }

    @Test
    void doesNotReportNonReadyCorrectionAsStaffResubmittable() {
        assertStaffResubmissionReady(
                LoanCorrectionRequestStatus.OPEN,
                List.of(task(1, LoanCorrectionResponsibility.STAFF, LoanCorrectionTaskStatus.COMPLETED)),
                false
        );
    }

    private void assertStaffResubmissionReady(
            LoanCorrectionRequestStatus status,
            List<LoanCorrectionTask> tasks,
            boolean expected
    ) {
        when(corrections.findRequestsByApplicationId(APPLICATION_ID))
                .thenReturn(List.of(request(status)));
        when(corrections.findTasksByRequestId(REQUEST_ID)).thenReturn(tasks);

        var result = service.query(APPLICATION_ID).correctionRequest();

        assertEquals(expected, result.staffResubmissionReady());
    }

    private static LoanCorrectionRequest request(LoanCorrectionRequestStatus status) {
        return new LoanCorrectionRequest(
                REQUEST_ID, APPLICATION_ID, UUID.randomUUID(), "REQUEST_CORRECTION",
                CorrectionReasonCode.DOCUMENT_REVIEW_REQUIRED, UUID.randomUUID(), status,
                null, NOW.minusHours(3), null, null
        );
    }

    @Test
    void returnsEveryCorrectionAndSafeBatchedCreatorCompleterAndExactResubmitter() {
        var application = application();
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        UUID customerUserId = UUID.randomUUID();
        UUID oldRequestId = UUID.randomUUID();
        var old = historicalRequest(oldRequestId, NOW.minusDays(1), NOW.minusHours(2));
        var latest = new LoanCorrectionRequest(REQUEST_ID, APPLICATION_ID, UUID.randomUUID(), "REQUEST_STAFF_CORRECTION",
                CorrectionReasonCode.DOCUMENT_REVIEW_REQUIRED, CREATOR_ID, LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION,
                null, NOW.minusHours(1), NOW, null);
        var completed = new LoanCorrectionTask(UUID.randomUUID(), oldRequestId, 1, LoanCorrectionResponsibility.CUSTOMER,
                LoanCorrectionScope.DOCUMENT_REPLACEMENT, DocumentType.BANK_STATEMENT, false, ITEM_ID, VERSION_ID,
                "Replace statement.", null, LoanCorrectionTaskStatus.COMPLETED, customerUserId, UUID.randomUUID(),
                NOW.minusHours(3), NOW.minusDays(1));
        var staffTask = task(2, LoanCorrectionResponsibility.STAFF, LoanCorrectionTaskStatus.COMPLETED);
        when(corrections.findRequestsByApplicationId(APPLICATION_ID)).thenReturn(List.of(old, latest));
        when(corrections.findTasksByRequestId(oldRequestId)).thenReturn(List.of(completed));
        when(corrections.findTasksByRequestId(REQUEST_ID)).thenReturn(List.of(staffTask));
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(APPLICATION_ID))
                .thenReturn(List.of(resubmission(customerUserId, old.resubmittedAt())));
        when(actors.findByUserIds(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Map.of(
                CREATOR_ID, staffSummary(CREATOR_ID), staffTask.completedByUserId(), staffSummary(staffTask.completedByUserId()),
                customerUserId, new com.meridian.platform.loan.application.port.out.WorkflowActorDirectoryPort
                        .ActorSummary(customerUserId, "CUSTOMER", application.customerId(), null)));

        var result = service.query(APPLICATION_ID);
        assertEquals(REQUEST_ID, result.correctionRequest().correctionRequestId());
        assertEquals(true, result.correctionRequest().makerCheckerBlockedForCurrentActor());
        assertEquals(true, result.correctionRequest().staffResubmissionReady());
        assertEquals(List.of(oldRequestId, REQUEST_ID), result.correctionHistory().stream().map(value -> value.correctionRequestId()).toList());
        var history = result.correctionHistory().getFirst();
        assertEquals(old.sourceReviewCycleId(), history.sourceReviewCycleId());
        assertEquals(old.sourceAction(), history.sourceAction());
        assertEquals("STAFF", history.createdBy().actorType());
        assertEquals("CUSTOMER_SELF_SERVICE", history.tasks().getFirst().completedBy().actorType());
        assertNull(history.tasks().getFirst().completedBy().staffActor());
        assertEquals(VERSION_ID, history.tasks().getFirst().baselineDocumentVersionId());
        assertEquals("Replace statement.", history.tasks().getFirst().customerInstruction());
        assertEquals("CUSTOMER_SELF_SERVICE", history.resubmittedBy().actorType());
        assertEquals("SUBMITTED", history.resultingApplicationStatus());
        assertEquals(old.resubmittedAt(), history.resubmittedAt());
        assertEquals(old.readyAt(), history.readyAt());
        String json = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().writeValueAsString(result);
        org.junit.jupiter.api.Assertions.assertFalse(json.contains(customerUserId.toString()));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("resubmissionRequestId"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("completionRequestId"));
        org.mockito.Mockito.verify(actors).findByUserIds(Set.of(CREATOR_ID, staffTask.completedByUserId(), customerUserId));
        org.mockito.Mockito.verify(corrections, org.mockito.Mockito.never()).saveRequest(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(corrections, org.mockito.Mockito.never()).saveTask(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(applications, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completedAssistedCustomerTaskPreservesStaffRecorderAndOrdersTasksBySequence() {
        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application(OriginationChannel.STAFF_ASSISTED)));
        var request = historicalRequest(REQUEST_ID, NOW.minusDays(1), NOW);
        var second = task(2, LoanCorrectionResponsibility.CUSTOMER, LoanCorrectionTaskStatus.COMPLETED);
        var first = task(1, LoanCorrectionResponsibility.CUSTOMER, LoanCorrectionTaskStatus.COMPLETED);
        when(corrections.findRequestsByApplicationId(APPLICATION_ID)).thenReturn(List.of(request));
        when(corrections.findTasksByRequestId(REQUEST_ID)).thenReturn(List.of(second, first));
        when(actors.findByUserIds(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Map.of(
                CREATOR_ID, staffSummary(CREATOR_ID), first.completedByUserId(), staffSummary(first.completedByUserId()),
                second.completedByUserId(), staffSummary(second.completedByUserId())));
        var history = service.query(APPLICATION_ID).correctionHistory().getFirst();
        assertEquals(List.of(1, 2), history.tasks().stream().map(value -> value.sequence()).toList());
        assertEquals("CUSTOMER", history.tasks().getFirst().responsibleParty());
        assertEquals("STAFF", history.tasks().getFirst().completedBy().actorType());
        assertEquals(first.completedByUserId(), history.tasks().getFirst().completedBy().staffActor().userId());
        assertEquals("UNAVAILABLE", history.resubmittedBy().actorType());
        assertEquals(false, service.query(APPLICATION_ID).correctionRequest().staffResubmissionReady());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ambiguous", "wrong-time", "wrong-state", "shared-time", "missing"})
    void resubmitterIsUnavailableUnlessExistingDurableAssociationIsExactAndUnique(String scenario) {
        var request = historicalRequest(REQUEST_ID, NOW.minusDays(1), NOW);
        var transition = resubmission(CREATOR_ID, "wrong-time".equals(scenario) ? NOW.plusNanos(1000) : NOW);
        if ("wrong-state".equals(scenario)) transition = new com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition(
                transition.id(), APPLICATION_ID, transition.operationId(), 1, LoanApplicationStatus.UNDER_REVIEW,
                transition.toStatus(), transition.action(), null, transition.actorType(), CREATOR_ID, NOW);
        when(corrections.findRequestsByApplicationId(APPLICATION_ID)).thenReturn("shared-time".equals(scenario)
                ? List.of(historicalRequest(UUID.randomUUID(), NOW.minusDays(2), NOW), request) : List.of(request));
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(APPLICATION_ID)).thenReturn(
                "ambiguous".equals(scenario) ? List.of(transition, resubmission(CREATOR_ID, NOW))
                        : "missing".equals(scenario) ? List.of() : List.of(transition));
        var result = service.query(APPLICATION_ID).correctionHistory().getLast();
        assertEquals(NOW, result.resubmittedAt());
        assertEquals("UNAVAILABLE", result.resubmittedBy().actorType());
        assertNull(result.resultingApplicationStatus());
    }

    @Test
    void foreignCustomerIdentityFailsClosedWithoutExposure() {
        UUID foreignUserId = UUID.randomUUID();
        var request = historicalRequest(REQUEST_ID, NOW.minusDays(1), NOW);
        when(corrections.findRequestsByApplicationId(APPLICATION_ID)).thenReturn(List.of(request));
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(APPLICATION_ID))
                .thenReturn(List.of(resubmission(foreignUserId, NOW)));
        when(actors.findByUserIds(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Map.of(foreignUserId,
                new com.meridian.platform.loan.application.port.out.WorkflowActorDirectoryPort.ActorSummary(
                        foreignUserId, "CUSTOMER", UUID.randomUUID(), null)));
        org.junit.jupiter.api.Assertions.assertThrows(com.meridian.platform.shared.domain.exception.BusinessStateConflictException.class,
                () -> service.query(APPLICATION_ID));
    }

    @Test
    void preservesExistingLatestActionProjectionInsteadOfSelectingAnActionFromHistoryOrder() {
        var existingLatest = historicalRequest(REQUEST_ID, NOW, NOW.plusSeconds(1));
        var other = historicalRequest(UUID.randomUUID(), NOW, NOW.plusSeconds(2));
        when(corrections.findRequestsByApplicationId(APPLICATION_ID)).thenReturn(List.of(existingLatest, other));
        org.mockito.Mockito.doReturn(Optional.of(existingLatest)).when(corrections).findLatestRequestByApplicationId(APPLICATION_ID);
        var result = service.query(APPLICATION_ID);
        assertEquals(REQUEST_ID, result.correctionRequest().correctionRequestId());
        assertEquals(List.of(REQUEST_ID, other.id()), result.correctionHistory().stream().map(row -> row.correctionRequestId()).toList());
    }

    @Test
    void identifiesAProvenSystemResubmissionWithoutManufacturingAUser() {
        var request = historicalRequest(REQUEST_ID, NOW.minusDays(1), NOW);
        when(corrections.findRequestsByApplicationId(APPLICATION_ID)).thenReturn(List.of(request));
        when(transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(APPLICATION_ID)).thenReturn(List.of(
                new com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition(UUID.randomUUID(), APPLICATION_ID,
                        UUID.randomUUID(), 1, LoanApplicationStatus.RETURNED_FOR_REVISION, LoanApplicationStatus.SUBMITTED,
                        com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction.RESUBMIT_CORRECTION, null,
                        com.meridian.platform.shared.domain.model.ActorType.SYSTEM, null, NOW)));
        var actor = service.query(APPLICATION_ID).correctionHistory().getFirst().resubmittedBy();
        assertEquals("SYSTEM", actor.actorType());
        assertNull(actor.staffActor());
    }

    private static LoanCorrectionRequest historicalRequest(UUID id, LocalDateTime created, LocalDateTime resubmitted) {
        return new LoanCorrectionRequest(id, APPLICATION_ID, UUID.randomUUID(), "REQUEST_CORRECTION",
                CorrectionReasonCode.DOCUMENT_REVIEW_REQUIRED, CREATOR_ID, LoanCorrectionRequestStatus.RESUBMITTED,
                UUID.randomUUID(), created, resubmitted.minusMinutes(1), resubmitted);
    }

    private static com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition resubmission(UUID actor, LocalDateTime at) {
        return new com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition(UUID.randomUUID(), APPLICATION_ID,
                UUID.randomUUID(), 1, LoanApplicationStatus.RETURNED_FOR_REVISION, LoanApplicationStatus.SUBMITTED,
                com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction.RESUBMIT_CORRECTION, null,
                com.meridian.platform.shared.domain.model.ActorType.USER, actor, at);
    }

    private static com.meridian.platform.loan.application.port.out.WorkflowActorDirectoryPort.ActorSummary staffSummary(UUID id) {
        return new com.meridian.platform.loan.application.port.out.WorkflowActorDirectoryPort.ActorSummary(id, "STAFF", null,
                new com.meridian.platform.loan.application.port.out.StaffActorSummary(id, "Deni Loan Officer", "deni@meridian.test"));
    }

    private static LoanCorrectionTask task(
            int sequence,
            LoanCorrectionResponsibility responsibility,
            LoanCorrectionTaskStatus status
    ) {
        LocalDateTime completedAt = status == LoanCorrectionTaskStatus.COMPLETED ? NOW.minusHours(1) : null;
        return new LoanCorrectionTask(
                UUID.randomUUID(), REQUEST_ID, sequence, responsibility,
                responsibility == LoanCorrectionResponsibility.STAFF
                        ? LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD
                        : LoanCorrectionScope.DOCUMENT_REPLACEMENT,
                DocumentType.BANK_STATEMENT, false, ITEM_ID, VERSION_ID,
                responsibility == LoanCorrectionResponsibility.CUSTOMER ? "Replace it." : null,
                responsibility == LoanCorrectionResponsibility.STAFF ? "Upload it." : null,
                status,
                status == LoanCorrectionTaskStatus.COMPLETED ? UUID.randomUUID() : null,
                status == LoanCorrectionTaskStatus.COMPLETED ? UUID.randomUUID() : null,
                completedAt,
                NOW.minusHours(2)
        );
    }

    private static LoanApplication application() {
        return application(OriginationChannel.CUSTOMER_DIGITAL);
    }

    private static LoanApplication application(OriginationChannel channel) {
        return new LoanApplication(
                APPLICATION_ID, UUID.randomUUID(), UUID.randomUUID(), "MER-2026-000001",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
                channel, LoanApplicationStatus.RETURNED_FOR_REVISION,
                BigDecimal.valueOf(10_000_000), 12, NOW.minusDays(2));
    }

    private static AuthenticatedUser staff(UUID userId) {
        return staff(userId, Set.of("loan:correction:staff"));
    }

    private static AuthenticatedUser staff(UUID userId, Set<String> permissions) {
        return new AuthenticatedUser(
                userId, "staff@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), permissions);
    }
}
