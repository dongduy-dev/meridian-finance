package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationDto;
import com.meridian.platform.loan.application.dto.SalaryAdvanceApplicationRequest;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.in.StartSalaryAdvanceApplicationUseCase;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.PartnerEligibilityPort;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationTransitionResult;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.SalaryAdvanceApplicationCreationResult;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import com.meridian.platform.loan.domain.model.VerifiedPartnerEmployeeLinkSnapshot;
import com.meridian.platform.loan.domain.service.SalaryAdvanceApplicationPolicy;
import com.meridian.platform.shared.application.audit.AuditAction;
import com.meridian.platform.shared.application.audit.AuditEntityType;
import com.meridian.platform.shared.application.audit.AuditEventPublisher;
import com.meridian.platform.shared.application.audit.AuditPayloadEntry;
import com.meridian.platform.shared.application.audit.AuditPayloadKey;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActionActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class StartSalaryAdvanceApplicationService implements StartSalaryAdvanceApplicationUseCase {

    private static final DateTimeFormatter APPLICATION_NUMBER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final LoanProductRepository loanProductRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final SalaryAdvanceLimitRepository salaryAdvanceLimitRepository;
    private final SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository;
    private final SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository;
    private final PartnerEligibilityPort partnerEligibilityPort;
    private final LoanMapper loanMapper;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final LoanApplicationLifecycleHistoryRecorder historyRecorder;
    private final AuditEventPublisher auditEventPublisher;
    private final SalaryAdvanceApplicationPolicy applicationPolicy = new SalaryAdvanceApplicationPolicy();

    public StartSalaryAdvanceApplicationService(
            LoanProductRepository loanProductRepository,
            LoanApplicationRepository loanApplicationRepository,
            SalaryAdvanceLimitRepository salaryAdvanceLimitRepository,
            SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository,
            SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository,
            PartnerEligibilityPort partnerEligibilityPort,
            LoanMapper loanMapper,
            CurrentUserProvider currentUserProvider,
            Clock clock,
            LoanApplicationLifecycleHistoryRecorder historyRecorder,
            AuditEventPublisher auditEventPublisher
    ) {
        this.loanProductRepository = loanProductRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.salaryAdvanceLimitRepository = salaryAdvanceLimitRepository;
        this.salaryAdvanceLimitMovementRepository = salaryAdvanceLimitMovementRepository;
        this.salaryAdvanceVerificationRepository = salaryAdvanceVerificationRepository;
        this.partnerEligibilityPort = partnerEligibilityPort;
        this.loanMapper = loanMapper;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
        this.historyRecorder = historyRecorder;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Override
    @Transactional
    public SalaryAdvanceApplicationDto startSalaryAdvanceApplication(SalaryAdvanceApplicationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.customerPartnerEmployeeLinkId(), "customerPartnerEmployeeLinkId must not be null");
        Objects.requireNonNull(request.requestedAmount(), "requestedAmount must not be null");
        Objects.requireNonNull(request.requestedTermMonths(), "requestedTermMonths must not be null");

        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        UUID customerId = currentUser.requireCustomerId();
        ActionActor actor = ActionActor.user(currentUser.userId());
        UUID operationId = UUID.randomUUID();

        LoanProduct salaryAdvanceProduct = loanProductRepository.findByProductCode(ProductCode.SALARY_ADVANCE)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PRODUCT_NOT_FOUND",
                        "Salary Advance product was not found."
                ));

        applicationPolicy.validateProduct(salaryAdvanceProduct);
        applicationPolicy.validateRequestedTerm(request.requestedTermMonths());
        applicationPolicy.validateRequestedAmount(salaryAdvanceProduct, request.requestedAmount());
        assertNoBlockingApplicationExists(customerId);

        VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot = partnerEligibilityPort.findVerifiedEmployeeLink(
                        customerId,
                        request.customerPartnerEmployeeLinkId()
                )
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "EMPLOYEE_NOT_VERIFIED",
                        "Customer must have a verified active employee link before creating a Salary Advance application."
                ));

        BigDecimal effectiveTotalLimit = applicationPolicy.calculateEffectiveTotalLimit(
                salaryAdvanceProduct,
                partnerSnapshot
        );

        salaryAdvanceLimitRepository.acquireCustomerLinkLock(
                customerId,
                request.customerPartnerEmployeeLinkId()
        );
        assertNoBlockingApplicationExists(customerId);

        LocalDateTime now = LocalDateTime.now(clock);
        LimitResolution limitResolution = findOrCreateLimit(customerId, request, partnerSnapshot, effectiveTotalLimit, now);
        SalaryAdvanceLimit reservedLimit = limitResolution.limit().reserve(request.requestedAmount());

        long applicationSequence = loanApplicationRepository.nextApplicationNumberSequence();
        LoanApplicationTransitionResult applicationTransition = LoanApplication.submittedWithTransition(
                UUID.randomUUID(),
                customerId,
                salaryAdvanceProduct,
                formatApplicationNumber(applicationSequence, now),
                request.requestedAmount(),
                request.requestedTermMonths(),
                now
        );

        LoanApplication savedApplication = loanApplicationRepository.save(applicationTransition.loanApplication());
        SalaryAdvanceLimit savedReservedLimit = salaryAdvanceLimitRepository.save(reservedLimit);
        SalaryAdvanceLimitMovement reservedMovement = salaryAdvanceLimitMovementRepository.save(SalaryAdvanceLimitMovement.reserved(
                UUID.randomUUID(),
                savedReservedLimit.id(),
                savedApplication.id(),
                request.requestedAmount(),
                now
        ));

        SalaryAdvanceVerification verification = SalaryAdvanceVerification.verified(
                UUID.randomUUID(),
                savedApplication,
                savedReservedLimit,
                partnerSnapshot,
                now
        );
        SalaryAdvanceVerification savedVerification = salaryAdvanceVerificationRepository.save(verification);

        historyRecorder.record(operationId, actor, null, now, applicationTransition);
        short auditSequenceNumber = 1;
        if (limitResolution.movement() != null) {
            publishLimitMovementAudit(
                    operationId, auditSequenceNumber++, actor, limitResolution.limit(), limitResolution.movement(), now
            );
        }
        auditEventPublisher.publish(new AuditRecordRequestedEvent(
                operationId, auditSequenceNumber++, actor, AuditEntityType.LOAN_APPLICATION, savedApplication.id(),
                AuditAction.APPLICATION_SUBMITTED,
                List.of(new AuditPayloadEntry(AuditPayloadKey.PRODUCT_CODE, savedApplication.productCode().name())), now
        ));
        publishLimitMovementAudit(operationId, auditSequenceNumber, actor, savedReservedLimit, reservedMovement, now);

        return loanMapper.toSalaryAdvanceApplicationDto(new SalaryAdvanceApplicationCreationResult(
                savedApplication,
                savedReservedLimit,
                savedVerification
        ));
    }

    private void assertNoBlockingApplicationExists(UUID customerId) {
        if (loanApplicationRepository.existsByCustomerIdAndProductCodeAndStatusIn(
                customerId,
                ProductCode.SALARY_ADVANCE,
                LoanApplicationStatus.blockingStatuses()
        )) {
            throw new BusinessStateConflictException(
                    "BLOCKING_APPLICATION_EXISTS",
                    "A blocking Salary Advance application already exists for this customer."
            );
        }
    }

    private LimitResolution findOrCreateLimit(
            UUID customerId,
            SalaryAdvanceApplicationRequest request,
            VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot,
            BigDecimal effectiveTotalLimit,
            LocalDateTime occurredAt
    ) {
        return salaryAdvanceLimitRepository.findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
                        customerId,
                        request.customerPartnerEmployeeLinkId()
                )
                .map(currentLimit -> refreshLimitIfNeeded(
                        currentLimit,
                        effectiveTotalLimit,
                        partnerSnapshot.lastRefreshedAt(),
                        occurredAt
                ))
                .orElseGet(() -> initializeLimit(customerId, request, partnerSnapshot, effectiveTotalLimit, occurredAt));
    }

    private LimitResolution initializeLimit(
            UUID customerId,
            SalaryAdvanceApplicationRequest request,
            VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot,
            BigDecimal effectiveTotalLimit,
            LocalDateTime occurredAt
    ) {
        SalaryAdvanceLimit initializedLimit = SalaryAdvanceLimit.initialized(
                UUID.randomUUID(),
                customerId,
                request.customerPartnerEmployeeLinkId(),
                effectiveTotalLimit,
                partnerSnapshot.lastRefreshedAt()
        );

        SalaryAdvanceLimit savedLimit = salaryAdvanceLimitRepository.save(initializedLimit);
        SalaryAdvanceLimitMovement movement = salaryAdvanceLimitMovementRepository.save(SalaryAdvanceLimitMovement.initialized(
                UUID.randomUUID(),
                savedLimit,
                occurredAt
        ));
        return new LimitResolution(savedLimit, movement);
    }

    private LimitResolution refreshLimitIfNeeded(
            SalaryAdvanceLimit currentLimit,
            BigDecimal effectiveTotalLimit,
            LocalDateTime lastRefreshedAt,
            LocalDateTime occurredAt
    ) {
        SalaryAdvanceLimit refreshedLimit = currentLimit.refreshTotalLimit(effectiveTotalLimit, lastRefreshedAt);
        if (!hasLimitRefreshChange(currentLimit, refreshedLimit)) {
            return new LimitResolution(currentLimit, null);
        }

        SalaryAdvanceLimit savedLimit = salaryAdvanceLimitRepository.save(refreshedLimit);
        SalaryAdvanceLimitMovement movement = null;
        if (amountChanged(currentLimit.totalLimit(), savedLimit.totalLimit())) {
            movement = salaryAdvanceLimitMovementRepository.save(SalaryAdvanceLimitMovement.refreshed(
                    UUID.randomUUID(),
                    savedLimit,
                    occurredAt
            ));
        }
        return new LimitResolution(savedLimit, movement);
    }

    private void publishLimitMovementAudit(
            UUID operationId,
            short sequenceNumber,
            ActionActor actor,
            SalaryAdvanceLimit limit,
            SalaryAdvanceLimitMovement movement,
            LocalDateTime occurredAt
    ) {
        auditEventPublisher.publish(new AuditRecordRequestedEvent(
                operationId,
                sequenceNumber,
                actor,
                AuditEntityType.SALARY_ADVANCE_LIMIT_MOVEMENT,
                movement.id(),
                auditActionFor(movement),
                List.of(
                        new AuditPayloadEntry(AuditPayloadKey.SALARY_ADVANCE_LIMIT_ID, limit.id().toString()),
                        new AuditPayloadEntry(AuditPayloadKey.MOVEMENT_TYPE, movement.movementType().name())
                ),
                occurredAt
        ));
    }

    private AuditAction auditActionFor(SalaryAdvanceLimitMovement movement) {
        return switch (movement.movementType()) {
            case INITIALIZED -> AuditAction.SALARY_ADVANCE_LIMIT_INITIALIZED;
            case REFRESHED -> AuditAction.SALARY_ADVANCE_LIMIT_REFRESHED;
            case RESERVED -> AuditAction.SALARY_ADVANCE_LIMIT_RESERVED;
            default -> throw new IllegalArgumentException("Unsupported Salary Advance submission movement audit: "
                    + movement.movementType());
        };
    }

    private boolean hasLimitRefreshChange(SalaryAdvanceLimit currentLimit, SalaryAdvanceLimit refreshedLimit) {
        return amountChanged(currentLimit.totalLimit(), refreshedLimit.totalLimit())
                || amountChanged(currentLimit.availableAmount(), refreshedLimit.availableAmount())
                || !Objects.equals(currentLimit.lastRefreshedAt(), refreshedLimit.lastRefreshedAt());
    }

    private boolean amountChanged(BigDecimal first, BigDecimal second) {
        return first.compareTo(second) != 0;
    }

    private String formatApplicationNumber(long sequence, LocalDateTime submittedAt) {
        return "SA-" + submittedAt.format(APPLICATION_NUMBER_DATE_FORMAT) + "-" + String.format("%06d", sequence);
    }

    private record LimitResolution(SalaryAdvanceLimit limit, SalaryAdvanceLimitMovement movement) {
    }
}