package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.StaffCorrectionCaseDto;
import com.meridian.platform.loan.application.port.in.QueryStaffCorrectionCaseUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class QueryStaffCorrectionCaseService implements QueryStaffCorrectionCaseUseCase {
    private final LoanApplicationRepository applications;
    private final LoanCorrectionRepository corrections;
    private final LoanDocumentChecklistPort documents;
    private final CustomerCorrectionDocumentProof customerDocumentProof;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffCorrectionCaseService(
            LoanApplicationRepository applications,
            LoanCorrectionRepository corrections,
            LoanDocumentChecklistPort documents,
            CustomerCorrectionDocumentProof customerDocumentProof,
            CurrentUserProvider currentUserProvider
    ) {
        this.applications = applications;
        this.corrections = corrections;
        this.documents = documents;
        this.customerDocumentProof = customerDocumentProof;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffCorrectionCaseDto query(UUID loanApplicationId) {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        requireAuthority(actor);
        LoanApplication application = applications.findById(loanApplicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "LOAN_APPLICATION_NOT_FOUND", "Loan Application was not found."));
        LoanCorrectionRequest request = corrections.findLatestRequestByApplicationId(loanApplicationId)
                .orElse(null);
        return new StaffCorrectionCaseDto(
                application.id(), application.applicationNumber(), application.productCode().name(),
                application.originationChannel().name(), application.status().name(),
                request == null ? null : toRequest(application, request, actor));
    }

    private StaffCorrectionCaseDto.CorrectionRequestDto toRequest(
            LoanApplication application,
            LoanCorrectionRequest request,
            AuthenticatedUser actor
    ) {
        List<LoanCorrectionTask> tasks = corrections.findTasksByRequestId(request.id());
        boolean hasStaffTasks = tasks.stream()
                .anyMatch(task -> task.responsibleParty() == LoanCorrectionResponsibility.STAFF);
        boolean hasCustomerTasks = tasks.stream()
                .anyMatch(task -> task.responsibleParty() == LoanCorrectionResponsibility.CUSTOMER);
        boolean assistedCustomerTasks = hasCustomerTasks
                && application.permitsStaffMediatedCustomerCorrection();
        boolean allComplete = !tasks.isEmpty() && tasks.stream()
                .allMatch(task -> task.status() == LoanCorrectionTaskStatus.COMPLETED);
        boolean makerCheckerBlocked = hasStaffTasks
                && request.createdByUserId().equals(actor.userId());
        return new StaffCorrectionCaseDto.CorrectionRequestDto(
                request.id(), request.status().name(), request.reasonCode().name(), request.createdAt(),
                makerCheckerBlocked, allComplete,
                (hasStaffTasks || assistedCustomerTasks)
                        && request.status() == LoanCorrectionRequestStatus.READY_FOR_RESUBMISSION
                        && allComplete,
                tasks.stream().map(task -> toTask(
                        application, request, task, tasks, actor, makerCheckerBlocked)).toList());
    }

    private StaffCorrectionCaseDto.TaskDto toTask(
            LoanApplication application,
            LoanCorrectionRequest request,
            LoanCorrectionTask task,
            List<LoanCorrectionTask> requestTasks,
            AuthenticatedUser actor,
            boolean makerCheckerBlocked
    ) {
        boolean customerSourceViaStaff = task.responsibleParty() == LoanCorrectionResponsibility.CUSTOMER
                && application.permitsStaffMediatedCustomerCorrection();
        String proofState = proofState(application, task, requestTasks, customerSourceViaStaff);
        boolean open = task.status() == LoanCorrectionTaskStatus.OPEN;
        boolean uploadActionAvailable = open
                && task.scope() != LoanCorrectionScope.DOCUMENT_REVIEW
                && ((customerSourceViaStaff
                && actor.hasPermission("document:upload:assisted-correction"))
                || (task.responsibleParty() == LoanCorrectionResponsibility.STAFF
                && actor.hasPermission("document:upload:staff")));
        boolean completionActionAvailable = open
                && "SATISFIED".equals(proofState)
                && (customerSourceViaStaff
                || (task.responsibleParty() == LoanCorrectionResponsibility.STAFF
                && !makerCheckerBlocked));
        return new StaffCorrectionCaseDto.TaskDto(
                task.id(), task.responsibleParty().name(), task.status().name(), task.scope().name(),
                task.documentType() == null ? null : task.documentType().name(), task.checklistItemId(),
                task.baselineDocumentVersionId(), request.reasonCode().name(),
                customerSourceViaStaff ? task.customerInstruction() : null,
                task.responsibleParty() == LoanCorrectionResponsibility.STAFF
                        ? task.staffInstruction() : null,
                task.createdAt(), task.completedAt(), proofState, customerSourceViaStaff,
                uploadActionAvailable, completionActionAvailable);
    }

    private String proofState(
            LoanApplication application,
            LoanCorrectionTask task,
            List<LoanCorrectionTask> requestTasks,
            boolean customerSourceViaStaff
    ) {
        if (task.responsibleParty() != LoanCorrectionResponsibility.STAFF && !customerSourceViaStaff) {
            return "NOT_APPLICABLE";
        }
        if (task.status() == LoanCorrectionTaskStatus.COMPLETED) return "SATISFIED";
        if (customerSourceViaStaff) {
            return customerDocumentProof.isSatisfied(application.id(), task) ? "SATISFIED" : "MISSING";
        }
        try {
            if (task.scope() == LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD) {
                documents.requireCurrentVersion(application.id(), task.checklistItemId());
                return "SATISFIED";
            }
            if (task.scope() == LoanCorrectionScope.DOCUMENT_REVIEW) {
                boolean followsReplacement = requestTasks.stream().anyMatch(candidate ->
                        candidate.responsibleParty() == LoanCorrectionResponsibility.CUSTOMER
                                && candidate.scope() == LoanCorrectionScope.DOCUMENT_REPLACEMENT
                                && Objects.equals(candidate.checklistItemId(), task.checklistItemId())
                                && Objects.equals(candidate.baselineDocumentVersionId(),
                                task.baselineDocumentVersionId()));
                UUID versionToReview = task.baselineDocumentVersionId();
                if (followsReplacement) {
                    versionToReview = documents.requireCurrentVersion(
                            application.id(), task.checklistItemId());
                    if (Objects.equals(versionToReview, task.baselineDocumentVersionId())) return "MISSING";
                }
                return documents.isVersionReviewed(
                        application.id(), task.checklistItemId(), versionToReview)
                        ? "SATISFIED" : "MISSING";
            }
            return "MISSING";
        } catch (BusinessStateConflictException exception) {
            if ("DOCUMENT_UPLOAD_REQUIRED".equals(exception.getErrorCode())) return "MISSING";
            throw exception;
        }
    }

    private static void requireAuthority(AuthenticatedUser actor) {
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:correction:staff")) {
            throw new AuthorizationException(
                    "STAFF_CORRECTION_ACCESS_DENIED", "Staff correction permission is required.");
        }
    }
}
