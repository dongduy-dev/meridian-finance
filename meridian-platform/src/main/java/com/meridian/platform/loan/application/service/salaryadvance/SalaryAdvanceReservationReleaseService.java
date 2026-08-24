package com.meridian.platform.loan.application.service.salaryadvance;

import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.salaryadvance.ReservationReleaseTrigger;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceVerification;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class SalaryAdvanceReservationReleaseService {

    private final SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository;
    private final SalaryAdvanceLimitRepository salaryAdvanceLimitRepository;
    private final SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository;
    private final BusinessAuditPublisher businessAuditPublisher;

    public SalaryAdvanceReservationReleaseService(
            SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository,
            SalaryAdvanceLimitRepository salaryAdvanceLimitRepository,
            SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository,
            BusinessAuditPublisher businessAuditPublisher
    ) {
        this.salaryAdvanceVerificationRepository = salaryAdvanceVerificationRepository;
        this.salaryAdvanceLimitRepository = salaryAdvanceLimitRepository;
        this.salaryAdvanceLimitMovementRepository = salaryAdvanceLimitMovementRepository;
        this.businessAuditPublisher = businessAuditPublisher;
    }

    public Optional<SalaryAdvanceLimitMovement> releaseReservationOnce(
            LoanApplication loanApplication,
            BusinessOperationContext operationContext,
            ReservationReleaseTrigger trigger
    ) {
        if (loanApplication.productCode() != ProductCode.SALARY_ADVANCE) {
            return Optional.empty();
        }
        if (salaryAdvanceLimitMovementRepository.existsByLoanApplicationIdAndMovementType(
                loanApplication.id(),
                SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
        )) {
            return Optional.empty();
        }

        SalaryAdvanceVerification verification = salaryAdvanceVerificationRepository
                .findByLoanApplicationId(loanApplication.id())
                .orElseThrow(() -> new EntityNotFoundException(
                        "SALARY_ADVANCE_VERIFICATION_NOT_FOUND",
                        "Salary Advance verification was not found for the loan application."
                ));

        SalaryAdvanceLimit currentLimit = salaryAdvanceLimitRepository
                .findByCustomerIdAndCustomerPartnerEmployeeLinkIdForUpdate(
                        verification.customerId(),
                        verification.customerPartnerEmployeeLinkId()
                )
                .orElseThrow(() -> new EntityNotFoundException(
                        "SALARY_ADVANCE_LIMIT_NOT_FOUND",
                        "Salary Advance limit was not found for the loan application."
                ));

        SalaryAdvanceLimit releasedLimit = currentLimit.releaseReservation(loanApplication.requestedAmount());
        SalaryAdvanceLimit savedLimit = salaryAdvanceLimitRepository.save(releasedLimit);
        SalaryAdvanceLimitMovement savedMovement = salaryAdvanceLimitMovementRepository.save(
                SalaryAdvanceLimitMovement.reservationReleased(
                        UUID.randomUUID(),
                        savedLimit.id(),
                        loanApplication.id(),
                        loanApplication.requestedAmount(),
                        operationContext.occurredAt()
                )
        );
        businessAuditPublisher.publish(BusinessAuditEvent.single(
                operationContext,
                new BusinessAuditEntry(
                        BusinessAuditAction.RESERVATION_RELEASED,
                        BusinessAuditEntityType.SALARY_ADVANCE_LIMIT_MOVEMENT,
                        savedMovement.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.LOAN_APPLICATION_ID, loanApplication.id())
                                .put(BusinessAuditPayloadKey.SALARY_ADVANCE_LIMIT_ID, savedLimit.id())
                                .put(BusinessAuditPayloadKey.MOVEMENT_TYPE, savedMovement.movementType())
                                .put(BusinessAuditPayloadKey.RESERVATION_RELEASE_TRIGGER, trigger)
                                .build()
                )
        ));
        return Optional.of(savedMovement);
    }
}
