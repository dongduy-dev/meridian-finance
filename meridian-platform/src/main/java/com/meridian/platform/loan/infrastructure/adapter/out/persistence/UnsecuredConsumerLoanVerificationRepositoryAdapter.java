package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.UnsecuredConsumerLoanVerification;
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
    public Optional<UnsecuredConsumerLoanVerification> findByLoanApplicationId(UUID loanApplicationId) {
        return repository.findByLoanApplicationId(loanApplicationId)
                .map(UnsecuredConsumerLoanVerificationJpaEntity::toDomain);
    }
}
