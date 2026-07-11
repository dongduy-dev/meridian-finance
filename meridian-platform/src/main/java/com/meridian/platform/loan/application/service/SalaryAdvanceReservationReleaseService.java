package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitMovementRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceLimitRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimit;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovement;
import com.meridian.platform.loan.domain.model.SalaryAdvanceLimitMovementType;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import com.meridian.platform.shared.application.audit.AuditAction;
import com.meridian.platform.shared.application.audit.AuditEntityType;
import com.meridian.platform.shared.application.audit.AuditEventPublisher;
import com.meridian.platform.shared.application.audit.AuditPayloadEntry;
import com.meridian.platform.shared.application.audit.AuditPayloadKey;
import com.meridian.platform.shared.application.audit.AuditRecordRequestedEvent;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.model.ActionActor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SalaryAdvanceReservationReleaseService {

    private final SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository;
    private final SalaryAdvanceLimitRepository salaryAdvanceLimitRepository;
    private final SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository;
    private final AuditEventPublisher auditEventPublisher;

    public SalaryAdvanceReservationReleaseService(
            SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository,
            SalaryAdvanceLimitRepository salaryAdvanceLimitRepository,
            SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository,
            AuditEventPublisher auditEventPublisher
    ) {
        this.salaryAdvanceVerificationRepository = salaryAdvanceVerificationRepository;
        this.salaryAdvanceLimitRepository = salaryAdvanceLimitRepository;
        this.salaryAdvanceLimitMovementRepository = salaryAdvanceLimitMovementRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    public void releaseReservationOnce(
            LoanApplication loanApplication,
            LocalDateTime occurredAt,
            UUID operationId,
            ActionActor actor,
            short auditSequenceNumber
    ) {
        if (loanApplication.productCode() != ProductCode.SALARY_ADVANCE) {
            return;
        }
        if (salaryAdvanceLimitMovementRepository.existsByLoanApplicationIdAndMovementType(
                loanApplication.id(),
                SalaryAdvanceLimitMovementType.RESERVATION_RELEASED
        )) {
            return;
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

        SalaryAdvanceLimit savedLimit = salaryAdvanceLimitRepository.save(
                currentLimit.releaseReservation(loanApplication.requestedAmount())
        );
        SalaryAdvanceLimitMovement movement = salaryAdvanceLimitMovementRepository.save(
                SalaryAdvanceLimitMovement.reservationReleased(
                        UUID.randomUUID(), savedLimit.id(), loanApplication.id(), loanApplication.requestedAmount(), occurredAt
                )
        );
        auditEventPublisher.publish(new AuditRecordRequestedEvent(
                operationId,
                auditSequenceNumber,
                actor,
                AuditEntityType.SALARY_ADVANCE_LIMIT_MOVEMENT,
                movement.id(),
                AuditAction.SALARY_ADVANCE_RESERVATION_RELEASED,
                List.of(
                        new AuditPayloadEntry(AuditPayloadKey.SALARY_ADVANCE_LIMIT_ID, savedLimit.id().toString()),
                        new AuditPayloadEntry(AuditPayloadKey.MOVEMENT_TYPE, movement.movementType().name())
                ),
                occurredAt
        ));
    }
}
