package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceVerification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class SalaryAdvanceVerificationRepositoryAdapter implements SalaryAdvanceVerificationRepository {

    private final JpaSalaryAdvanceVerificationRepository jpaSalaryAdvanceVerificationRepository;

    public SalaryAdvanceVerificationRepositoryAdapter(
            JpaSalaryAdvanceVerificationRepository jpaSalaryAdvanceVerificationRepository
    ) {
        this.jpaSalaryAdvanceVerificationRepository = jpaSalaryAdvanceVerificationRepository;
    }

    @Override
    public SalaryAdvanceVerification save(SalaryAdvanceVerification salaryAdvanceVerification) {
        return toDomain(jpaSalaryAdvanceVerificationRepository.save(
                new SalaryAdvanceVerificationJpaEntity(salaryAdvanceVerification)
        ));
    }

    @Override
    public Optional<SalaryAdvanceVerification> findByLoanApplicationId(UUID loanApplicationId) {
        return jpaSalaryAdvanceVerificationRepository.findFirstByLoanApplicationIdOrderByVerificationSequenceDesc(loanApplicationId)
                .map(this::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SalaryAdvanceVerification> findByLoanApplicationIdForUpdate(UUID loanApplicationId) {
        return jpaSalaryAdvanceVerificationRepository
                .findLatestByLoanApplicationIdForUpdate(loanApplicationId)
                .map(this::toDomain);
    }

    private SalaryAdvanceVerification toDomain(SalaryAdvanceVerificationJpaEntity entity) {
        return new SalaryAdvanceVerification(
                entity.getId(),
                entity.getLoanApplicationId(),
                entity.getVerificationSequence(),
                entity.getCorrectionRequestId(),
                entity.getCustomerId(),
                entity.getCustomerPartnerEmployeeLinkId(),
                entity.getSalaryAdvanceLimitId(),
                entity.getPartnerCompanyId(),
                entity.getPartnerEmployeeId(),
                entity.getSourceImportBatchId(),
                entity.getEmployeeVerificationOutcome(),
                entity.getProductVerificationResult(),
                entity.getTotalLimitSnapshot(),
                entity.getUsedAmountSnapshot(),
                entity.getReservedAmountSnapshot(),
                entity.getAvailableLimitSnapshot(),
                entity.getVerifiedAt()
        );
    }
}
