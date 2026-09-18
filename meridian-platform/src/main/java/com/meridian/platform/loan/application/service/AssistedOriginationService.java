package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.AssistedOriginationCaseDto;
import com.meridian.platform.loan.application.dto.CreateAssistedOriginationCaseRequest;
import com.meridian.platform.loan.application.port.in.AuthorizeAssistedOriginationEvidenceUseCase;
import com.meridian.platform.loan.application.port.in.ManageAssistedOriginationUseCase;
import com.meridian.platform.loan.application.port.out.AssistedOriginationCaseRepository;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.CustomerReadinessSnapshot;
import com.meridian.platform.loan.domain.model.AssistedOriginationCase;
import com.meridian.platform.loan.domain.model.AssistedOriginationCaseStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AssistedOriginationService
        implements ManageAssistedOriginationUseCase, AuthorizeAssistedOriginationEvidenceUseCase {

    private static final String PERMISSION = "loan:originate:staff";

    private final AssistedOriginationCaseRepository cases;
    private final CustomerReadinessPort customerReadiness;
    private final CurrentUserProvider currentUsers;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public AssistedOriginationService(
            AssistedOriginationCaseRepository cases,
            CustomerReadinessPort customerReadiness,
            CurrentUserProvider currentUsers,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.cases = cases;
        this.customerReadiness = customerReadiness;
        this.currentUsers = currentUsers;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssistedOriginationCaseDto> findCases(AssistedOriginationCaseStatus status) {
        requireStaff();
        return cases.findAll(status).stream().map(AssistedOriginationService::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AssistedOriginationCaseDto getCase(UUID caseId) {
        requireStaff();
        return toDto(find(caseId, false));
    }

    @Override
    @Transactional
    public AssistedOriginationCaseDto createCase(CreateAssistedOriginationCaseRequest request) {
        AuthenticatedUser actor = requireStaff();
        Objects.requireNonNull(request, "request must not be null");
        requireSupported(request.productCode());
        if (request.customerId() != null) requireActiveCustomer(request.customerId());
        LocalDateTime now = LocalDateTime.now(clock);
        AssistedOriginationCase saved = cases.save(new AssistedOriginationCase(
                UUID.randomUUID(), request.productCode(), request.customerId(),
                AssistedOriginationCaseStatus.OPEN, actor.userId(), now, now, null, null
        ));
        publish(actor, now, BusinessAuditAction.ASSISTED_ORIGINATION_CASE_CREATED, saved);
        return toDto(saved);
    }

    @Override
    @Transactional
    public AssistedOriginationCaseDto associateCustomer(UUID caseId, UUID customerId) {
        AuthenticatedUser actor = requireStaff();
        requireActiveCustomer(customerId);
        LocalDateTime now = LocalDateTime.now(clock);
        AssistedOriginationCase saved = cases.save(find(caseId, true).associateCustomer(customerId, now));
        publish(actor, now, BusinessAuditAction.ASSISTED_ORIGINATION_CUSTOMER_ASSOCIATED, saved);
        return toDto(saved);
    }

    @Override
    @Transactional
    public AssistedOriginationCaseDto abandon(UUID caseId) {
        AuthenticatedUser actor = requireStaff();
        LocalDateTime now = LocalDateTime.now(clock);
        AssistedOriginationCase saved = cases.save(find(caseId, true).abandon(now));
        publish(actor, now, BusinessAuditAction.ASSISTED_ORIGINATION_CASE_ABANDONED, saved);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizedAssistedOrigination authorizeEvidenceRead(UUID caseId) {
        requireStaff();
        AssistedOriginationCase assistedCase = find(caseId, false);
        return new AuthorizedAssistedOrigination(assistedCase.id(), assistedCase.productCode().name());
    }

    @Override
    @Transactional
    public AuthorizedAssistedOrigination authorizeEvidenceMutation(UUID caseId) {
        requireStaff();
        AssistedOriginationCase assistedCase = find(caseId, true);
        assistedCase.requireOpen();
        return new AuthorizedAssistedOrigination(assistedCase.id(), assistedCase.productCode().name());
    }

    private AuthenticatedUser requireStaff() {
        AuthenticatedUser actor = currentUsers.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission(PERMISSION)) {
            throw new AuthorizationException("ASSISTED_ORIGINATION_ACCESS_DENIED",
                    "Staff-assisted origination access is denied.");
        }
        return actor;
    }

    private void requireSupported(ProductCode productCode) {
        if (productCode != ProductCode.UNSECURED_CONSUMER_LOAN
                && productCode != ProductCode.COLLATERAL_LOAN) {
            throw new BusinessRuleViolationException("ASSISTED_ORIGINATION_PRODUCT_NOT_ALLOWED",
                    "Product does not permit Staff-assisted origination.");
        }
    }

    private void requireActiveCustomer(UUID customerId) {
        CustomerReadinessSnapshot snapshot = customerReadiness.findReadinessByCustomerId(
                        Objects.requireNonNull(customerId, "customerId must not be null"))
                .orElseThrow(() -> new EntityNotFoundException("CUSTOMER_NOT_FOUND", "Customer was not found."));
        if (!snapshot.active()) {
            throw new BusinessStateConflictException("CUSTOMER_NOT_ACTIVE",
                    "Customer must be active for assisted origination.");
        }
    }

    private AssistedOriginationCase find(UUID caseId, boolean forUpdate) {
        return (forUpdate ? cases.findByIdForUpdate(caseId) : cases.findById(caseId))
                .orElseThrow(() -> new EntityNotFoundException(
                        "ASSISTED_ORIGINATION_CASE_NOT_FOUND", "Assisted origination case was not found."));
    }

    private void publish(
            AuthenticatedUser actor, LocalDateTime now, BusinessAuditAction action,
            AssistedOriginationCase assistedCase
    ) {
        BusinessAuditPayload.Builder payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.ASSISTED_ORIGINATION_CASE_ID, assistedCase.id())
                .put(BusinessAuditPayloadKey.PRODUCT_CODE, assistedCase.productCode())
                .put(BusinessAuditPayloadKey.ASSISTED_ORIGINATION_STATUS, assistedCase.status());
        if (assistedCase.customerId() != null) {
            payload.put(BusinessAuditPayloadKey.CUSTOMER_ID, assistedCase.customerId());
        }
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), now),
                new BusinessAuditEntry(action, BusinessAuditEntityType.ASSISTED_ORIGINATION_CASE,
                        assistedCase.id(), payload.build())
        ));
    }

    private static AssistedOriginationCaseDto toDto(AssistedOriginationCase value) {
        return new AssistedOriginationCaseDto(
                value.id(), value.productCode().name(), value.customerId(), value.status().name(),
                value.loanApplicationId(),
                value.createdByStaffUserId(), value.createdAt(), value.updatedAt(), value.terminalAt()
        );
    }
}
