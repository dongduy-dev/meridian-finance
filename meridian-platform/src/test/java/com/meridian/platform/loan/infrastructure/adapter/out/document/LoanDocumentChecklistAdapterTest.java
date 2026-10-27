package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.document.application.port.in.DocumentChecklistUseCase;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanDocumentType;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanDocumentChecklistAdapterTest {

    private final DocumentChecklistUseCase provider = mock(DocumentChecklistUseCase.class);
    private final LoanDocumentChecklistAdapter adapter = new LoanDocumentChecklistAdapter(provider);
    private final UUID application = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID version = UUID.randomUUID();
    private final BusinessOperationContext operation = BusinessOperationContext.system(UUID.randomUUID(), LocalDateTime.MIN);

    @ParameterizedTest
    @EnumSource(ProductCode.class)
    void mapsEveryProductAndSubmissionResult(ProductCode product) {
        var submission = DocumentChecklistUseCase.SubmissionProduct.valueOf(product.name());
        when(provider.resolveSubmissionInitialState(submission))
                .thenReturn(new DocumentChecklistUseCase.SubmissionChecklistInitialState(product == ProductCode.SALARY_ADVANCE));
        var requirements = Arrays.stream(LoanDocumentType.values())
                .map(type -> new DocumentChecklistUseCase.SubmissionEvidenceRequirement(type.name(), "REQUIRED")).toList();
        when(provider.resolveSubmissionRequirements(submission)).thenReturn(requirements);
        var items = requirements.stream().map(requirement -> new DocumentChecklistUseCase.SubmissionChecklistItemSnapshot(
                UUID.randomUUID(), requirement.documentType(), requirement.requirementStatus())).toList();
        when(provider.createSubmissionChecklist(application, submission, operation))
                .thenReturn(new DocumentChecklistUseCase.SubmissionChecklistSnapshot(items));

        assertEquals(product == ProductCode.SALARY_ADVANCE, adapter.resolveSubmissionInitialState(product).uploadComplete());
        assertEquals(requirements.stream().map(requirement -> new LoanDocumentChecklistPort.SubmissionEvidenceRequirement(
                LoanDocumentType.valueOf(requirement.documentType()), LoanDocumentChecklistPort.RequirementStatus.REQUIRED)).toList(),
                adapter.resolveSubmissionRequirements(product));
        assertEquals(items.stream().map(row -> new LoanDocumentChecklistPort.SubmissionChecklistItemSnapshot(
                row.checklistItemId(), LoanDocumentType.valueOf(row.documentType()),
                LoanDocumentChecklistPort.RequirementStatus.REQUIRED)).toList(), adapter.createSubmissionChecklist(application, product, operation).items());
        verify(provider).resolveSubmissionInitialState(submission);
        verify(provider).resolveSubmissionRequirements(submission);
        verify(provider).createSubmissionChecklist(application, submission, operation);
        verifyNoMoreInteractions(provider);
    }

    @ParameterizedTest
    @EnumSource(LoanDocumentChecklistPort.RequirementStatus.class)
    void preservesEveryRequirementCodeAndCurrentTargetOrder(LoanDocumentChecklistPort.RequirementStatus status) {
        var targets = Arrays.stream(LoanDocumentType.values()).map(type ->
                new DocumentChecklistUseCase.CurrentDocumentVersionTargetSnapshot(
                        UUID.randomUUID(), type.name(), status.name(), UUID.randomUUID())).toList();
        when(provider.currentVersionTargets(application)).thenReturn(targets);
        when(provider.resolveSubmissionRequirements(DocumentChecklistUseCase.SubmissionProduct.COLLATERAL_LOAN))
                .thenReturn(targets.stream().map(row -> new DocumentChecklistUseCase.SubmissionEvidenceRequirement(
                        row.documentType(), row.requirementStatus())).toList());
        when(provider.createSubmissionChecklist(application, DocumentChecklistUseCase.SubmissionProduct.COLLATERAL_LOAN, operation))
                .thenReturn(new DocumentChecklistUseCase.SubmissionChecklistSnapshot(targets.stream().map(row ->
                        new DocumentChecklistUseCase.SubmissionChecklistItemSnapshot(row.checklistItemId(), row.documentType(), row.requirementStatus())).toList()));

        assertEquals(targets.stream().map(row -> new LoanDocumentChecklistPort.CurrentDocumentVersionTargetSnapshot(
                row.checklistItemId(), LoanDocumentType.valueOf(row.documentType()), status, row.currentDocumentVersionId())).toList(),
                adapter.currentVersionTargets(application));
        assertTrue(adapter.resolveSubmissionRequirements(ProductCode.COLLATERAL_LOAN).stream().allMatch(row -> row.requirementStatus() == status));
        assertTrue(adapter.createSubmissionChecklist(application, ProductCode.COLLATERAL_LOAN, operation)
                .items().stream().allMatch(row -> row.requirementStatus() == status));
    }

    @ParameterizedTest
    @EnumSource(LoanDocumentType.class)
    void mapsRequiredItemAndExactVersionSnapshot(LoanDocumentType type) {
        when(provider.createRequiredItem(application, type.name(), operation)).thenReturn(item);
        when(provider.requireCurrentVersionSnapshot(application, item, version))
                .thenReturn(new DocumentChecklistUseCase.CurrentDocumentVersionSnapshot(type.name(), version));

        assertEquals(item, adapter.createRequiredItem(application, type, operation));
        assertEquals(new LoanDocumentChecklistPort.CurrentDocumentVersionSnapshot(type, version),
                adapter.requireCurrentVersionSnapshot(application, item, version));
        verify(provider).createRequiredItem(application, type.name(), operation);
        verify(provider).requireCurrentVersionSnapshot(application, item, version);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void delegatesBothVersionOverloadsAndKeepsEveryBooleanProofDistinct() {
        when(provider.requireCurrentVersion(application, item)).thenReturn(version);
        assertEquals(version, adapter.requireCurrentVersion(application, item));
        adapter.requireCurrentVersion(application, item, version);
        verify(provider).requireCurrentVersion(application, item);
        verify(provider).requireCurrentVersion(application, item, version);

        for (boolean result : List.of(false, true)) {
            when(provider.isProcessingReady(application)).thenReturn(result);
            when(provider.readiness(application)).thenReturn(new DocumentChecklistUseCase.ChecklistReadinessSnapshot(!result, result));
            when(provider.hasCurrentVersionDifferentFrom(item, version)).thenReturn(result);
            when(provider.isVersionAcceptedOrWaived(application, item, version)).thenReturn(result);
            when(provider.isVersionReviewed(application, item, version)).thenReturn(!result);
            assertEquals(result, adapter.isProcessingReady(application));
            assertEquals(new LoanDocumentChecklistPort.ChecklistReadinessSnapshot(!result, result), adapter.readiness(application));
            assertEquals(result, adapter.hasCurrentVersionDifferentFrom(item, version));
            assertEquals(result, adapter.isVersionAcceptedOrWaived(application, item, version));
            assertEquals(!result, adapter.isVersionReviewed(application, item, version));
        }
    }

    @Test
    void preservesProviderFailureIdentityForEveryOperation() {
        var failure = new BusinessStateConflictException("STALE_DOCUMENT_VERSION", "The expected document version is no longer current.");
        var failing = new LoanDocumentChecklistAdapter(mock(DocumentChecklistUseCase.class, invocation -> { throw failure; }));
        List<Executable> operations = List.of(
                () -> failing.resolveSubmissionInitialState(ProductCode.SALARY_ADVANCE),
                () -> failing.resolveSubmissionRequirements(ProductCode.COLLATERAL_LOAN),
                () -> failing.createSubmissionChecklist(application, ProductCode.UNSECURED_CONSUMER_LOAN, operation),
                () -> failing.isProcessingReady(application), () -> failing.readiness(application),
                () -> failing.currentVersionTargets(application),
                () -> failing.createRequiredItem(application, LoanDocumentType.RECENT_PAYSLIP, operation),
                () -> failing.requireCurrentVersion(application, item),
                () -> failing.requireCurrentVersion(application, item, version),
                () -> failing.requireCurrentVersionSnapshot(application, item, version),
                () -> failing.hasCurrentVersionDifferentFrom(item, version),
                () -> failing.isVersionAcceptedOrWaived(application, item, version),
                () -> failing.isVersionReviewed(application, item, version));
        for (var call : operations) assertSame(failure, assertThrows(BusinessStateConflictException.class, call));
    }
}
