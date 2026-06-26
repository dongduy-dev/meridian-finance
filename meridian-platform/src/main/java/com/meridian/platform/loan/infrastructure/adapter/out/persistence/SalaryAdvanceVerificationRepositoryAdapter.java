package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.domain.model.SalaryAdvanceVerification;
import org.springframework.stereotype.Repository;

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

    private SalaryAdvanceVerification toDomain(SalaryAdvanceVerificationJpaEntity entity) {
        return new SalaryAdvanceVerification(
                entity.getId(),
                entity.getLoanApplicationId(),
                entity.getCustomerId(),
                entity.getCustomerPartnerEmployeeLinkId(),
                entity.getSalaryAdvanceLimitId(),
                entity.getPartnerCompanyId(),
                entity.getPartnerEmployeeId(),
                entity.getSourceImportBatchId(),
                entity.getProductVerificationResult(),
                entity.getTotalLimitSnapshot(),
                entity.getUsedAmountSnapshot(),
                entity.getReservedAmountSnapshot(),
                entity.getAvailableLimitSnapshot(),
                entity.getVerifiedAt()
        );
    }
}
