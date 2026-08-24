package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanVerification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UnsecuredConsumerLoanVerificationRepositoryAdapter
        implements UnsecuredConsumerLoanVerificationRepository {

    private final JpaUnsecuredConsumerLoanVerificationRepository repository;

    public UnsecuredConsumerLoanVerificationRepositoryAdapter(
            JpaUnsecuredConsumerLoanVerificationRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public UnsecuredConsumerLoanVerification save(UnsecuredConsumerLoanVerification verification) {
        return repository.save(new UnsecuredConsumerLoanVerificationJpaEntity(verification)).toDomain();
    }

    @Override
    public Optional<UnsecuredConsumerLoanVerification> findLatestByLoanApplicationId(UUID loanApplicationId) {
        return repository.findFirstByLoanApplicationIdOrderByVerificationSequenceDesc(loanApplicationId)
                .map(UnsecuredConsumerLoanVerificationJpaEntity::toDomain);
    }

    @Override
    public Optional<UnsecuredConsumerLoanVerification> findLatestByLoanApplicationIdForUpdate(
            UUID loanApplicationId
    ) {
        return repository.findLatestForUpdate(loanApplicationId)
                .map(UnsecuredConsumerLoanVerificationJpaEntity::toDomain);
    }
}
