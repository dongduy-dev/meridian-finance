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
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SalaryAdvanceReservationReleaseService {

    private final SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository;
    private final SalaryAdvanceLimitRepository salaryAdvanceLimitRepository;
    private final SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository;

    public SalaryAdvanceReservationReleaseService(
            SalaryAdvanceVerificationRepository salaryAdvanceVerificationRepository,
            SalaryAdvanceLimitRepository salaryAdvanceLimitRepository,
            SalaryAdvanceLimitMovementRepository salaryAdvanceLimitMovementRepository
    ) {
        this.salaryAdvanceVerificationRepository = salaryAdvanceVerificationRepository;
        this.salaryAdvanceLimitRepository = salaryAdvanceLimitRepository;
        this.salaryAdvanceLimitMovementRepository = salaryAdvanceLimitMovementRepository;
    }

    public void releaseReservationOnce(LoanApplication loanApplication, LocalDateTime occurredAt) {
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

        SalaryAdvanceLimit releasedLimit = currentLimit.releaseReservation(loanApplication.requestedAmount());
        SalaryAdvanceLimit savedLimit = salaryAdvanceLimitRepository.save(releasedLimit);
        salaryAdvanceLimitMovementRepository.save(SalaryAdvanceLimitMovement.reservationReleased(
                UUID.randomUUID(),
                savedLimit.id(),
                loanApplication.id(),
                loanApplication.requestedAmount(),
                occurredAt
        ));
    }
}
