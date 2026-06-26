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
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.SalaryAdvanceApplicationCreationResult;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import com.meridian.platform.loan.domain.model.VerifiedPartnerEmployeeLinkSnapshot;
import com.meridian.platform.loan.domain.service.SalaryAdvanceApplicationPolicy;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final SalaryAdvanceApplicationPolicy applicationPolicy = new SalaryAdvanceApplicationPolicy();

    public StartSalaryAdvanceApplicationService(
            LoanProductRepository loanProductRepository,
            LoanApplicationRepository loanApplicationRepository,
            SalaryAdvanceLimitRepository salaryAdvanceLimitRepository,
            SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository,
            SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository,
            PartnerEligibilityPort partnerEligibilityPort,
            LoanMapper loanMapper
    ) {
        this.loanProductRepository = loanProductRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.salaryAdvanceLimitRepository = salaryAdvanceLimitRepository;
        this.salaryAdvanceLimitMovementRepository = salaryAdvanceLimitMovementRepository;
        this.salaryAdvanceVerificationRepository = salaryAdvanceVerificationRepository;
        this.partnerEligibilityPort = partnerEligibilityPort;
        this.loanMapper = loanMapper;
    }

    @Override
    @Transactional
    public SalaryAdvanceApplicationDto startSalaryAdvanceApplication(SalaryAdvanceApplicationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.customerId(), "customerId must not be null");
        Objects.requireNonNull(request.customerPartnerEmployeeLinkId(), "customerPartnerEmployeeLinkId must not be null");
        Objects.requireNonNull(request.requestedAmount(), "requestedAmount must not be null");
        Objects.requireNonNull(request.requestedTermMonths(), "requestedTermMonths must not be null");

        LoanProduct salaryAdvanceProduct = loanProductRepository.findByProductCode(ProductCode.SALARY_ADVANCE)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PRODUCT_NOT_FOUND",
                        "Salary Advance product was not found."
                ));

        applicationPolicy.validateProduct(salaryAdvanceProduct);
        applicationPolicy.validateRequestedTerm(request.requestedTermMonths());
        applicationPolicy.validateRequestedAmount(salaryAdvanceProduct, request.requestedAmount());

        if (loanApplicationRepository.existsByCustomerIdAndProductCodeAndStatusIn(
                request.customerId(),
                ProductCode.SALARY_ADVANCE,
                LoanApplicationStatus.blockingStatuses()
        )) {
            throw new BusinessStateConflictException(
                    "BLOCKING_APPLICATION_EXISTS",
                    "A blocking Salary Advance application already exists for this customer."
            );
        }

        VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot = partnerEligibilityPort.findVerifiedEmployeeLink(
                        request.customerId(),
                        request.customerPartnerEmployeeLinkId()
                )
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "EMPLOYEE_NOT_VERIFIED",
                        "Customer must have a verified active employee link before creating a Salary Advance application."
                ));

        applicationPolicy.validateEmployeeConfiguredLimit(partnerSnapshot, request.requestedAmount());

        salaryAdvanceLimitRepository.acquireCustomerLinkLock(
                request.customerId(),
                request.customerPartnerEmployeeLinkId()
        );

        LocalDateTime now = LocalDateTime.now();
        SalaryAdvanceLimit limit = findOrCreateLimit(request, partnerSnapshot, now);
        SalaryAdvanceLimit reservedLimit = limit.reserve(request.requestedAmount());

        long applicationSequence = loanApplicationRepository.nextApplicationNumberSequence();
        LoanApplication loanApplication = LoanApplication.submitted(
                UUID.randomUUID(),
                request.customerId(),
                salaryAdvanceProduct,
                formatApplicationNumber(applicationSequence, now),
                request.requestedAmount(),
                request.requestedTermMonths(),
                now
        );

        LoanApplication savedApplication = loanApplicationRepository.save(loanApplication);
        SalaryAdvanceLimit savedReservedLimit = salaryAdvanceLimitRepository.save(reservedLimit);
        salaryAdvanceLimitMovementRepository.save(SalaryAdvanceLimitMovement.reserved(
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

        return loanMapper.toSalaryAdvanceApplicationDto(new SalaryAdvanceApplicationCreationResult(
                savedApplication,
                savedReservedLimit,
                savedVerification
        ));
    }

    private SalaryAdvanceLimit findOrCreateLimit(
            SalaryAdvanceApplicationRequest request,
            VerifiedPartnerEmployeeLinkSnapshot partnerSnapshot,
            LocalDateTime occurredAt
    ) {
        return salaryAdvanceLimitRepository.findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
                        request.customerId(),
                        request.customerPartnerEmployeeLinkId()
                )
                .orElseGet(() -> {
                    SalaryAdvanceLimit initializedLimit = SalaryAdvanceLimit.initialized(
                            UUID.randomUUID(),
                            request.customerId(),
                            request.customerPartnerEmployeeLinkId(),
                            partnerSnapshot.employeeSalaryAdvanceLimit(),
                            partnerSnapshot.lastRefreshedAt()
                    );

                    SalaryAdvanceLimit savedLimit = salaryAdvanceLimitRepository.save(initializedLimit);
                    salaryAdvanceLimitMovementRepository.save(SalaryAdvanceLimitMovement.initialized(
                            UUID.randomUUID(),
                            savedLimit,
                            occurredAt
                    ));
                    return savedLimit;
                });
    }

    private String formatApplicationNumber(long sequence, LocalDateTime submittedAt) {
        return "SA-" + submittedAt.format(APPLICATION_NUMBER_DATE_FORMAT) + "-" + String.format("%06d", sequence);
    }
}
