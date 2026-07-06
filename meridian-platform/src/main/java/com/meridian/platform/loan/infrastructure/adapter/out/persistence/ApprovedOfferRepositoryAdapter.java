package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferFinancialTerms;
import com.meridian.platform.loan.domain.model.ProvisionalRepaymentItem;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ApprovedOfferRepositoryAdapter implements ApprovedOfferRepository {

    private final JpaApprovedOfferRepository jpaApprovedOfferRepository;
    private final JpaApprovedOfferRepaymentItemRepository jpaApprovedOfferRepaymentItemRepository;

    public ApprovedOfferRepositoryAdapter(
            JpaApprovedOfferRepository jpaApprovedOfferRepository,
            JpaApprovedOfferRepaymentItemRepository jpaApprovedOfferRepaymentItemRepository
    ) {
        this.jpaApprovedOfferRepository = jpaApprovedOfferRepository;
        this.jpaApprovedOfferRepaymentItemRepository = jpaApprovedOfferRepaymentItemRepository;
    }

    @Override
    public ApprovedOffer save(ApprovedOffer approvedOffer) {
        ApprovedOfferJpaEntity entity = jpaApprovedOfferRepository.findById(approvedOffer.id())
                .map(existingEntity -> {
                    existingEntity.updateFrom(approvedOffer);
                    return existingEntity;
                })
                .orElseGet(() -> new ApprovedOfferJpaEntity(approvedOffer));

        ApprovedOfferJpaEntity savedEntity = jpaApprovedOfferRepository.save(entity);
        jpaApprovedOfferRepaymentItemRepository.deleteByApprovedOfferId(savedEntity.getId());
        jpaApprovedOfferRepaymentItemRepository.saveAll(approvedOffer.repaymentItems()
                .stream()
                .map(item -> new ApprovedOfferRepaymentItemJpaEntity(savedEntity.getId(), item))
                .toList());
        return toDomain(savedEntity);
    }

    @Override
    public Optional<ApprovedOffer> findByLoanApplicationId(UUID loanApplicationId) {
        return jpaApprovedOfferRepository.findByLoanApplicationId(loanApplicationId)
                .map(this::toDomain);
    }

    @Override
    public Optional<ApprovedOffer> findByLoanApplicationIdForUpdate(UUID loanApplicationId) {
        return jpaApprovedOfferRepository.findByLoanApplicationIdForUpdate(loanApplicationId)
                .map(this::toDomain);
    }

    @Override
    public List<UUID> findExpiredPendingLoanApplicationIds(LocalDateTime now, int batchSize) {
        return jpaApprovedOfferRepository.findExpiredPendingLoanApplicationIds(now, batchSize);
    }

    private ApprovedOffer toDomain(ApprovedOfferJpaEntity entity) {
        List<ProvisionalRepaymentItem> items = jpaApprovedOfferRepaymentItemRepository
                .findByApprovedOfferIdOrderByInstallmentNumberAsc(entity.getId())
                .stream()
                .map(this::toDomain)
                .toList();

        return new ApprovedOffer(
                entity.getId(),
                entity.getLoanApplicationId(),
                entity.getSourceLoanProductPolicyId(),
                entity.getStatus(),
                new ApprovedOfferFinancialTerms(
                        entity.getApprovedPrincipal(),
                        entity.getApprovedTermMonths(),
                        entity.getInterestCalculationMethod(),
                        entity.getFlatMonthlyInterestRate(),
                        entity.getTotalInterest(),
                        entity.getFeeAmount(),
                        entity.getTotalRepaymentAmount(),
                        entity.getRepaymentMethod()
                ),
                items,
                entity.getGeneratedAt(),
                entity.getExpiresAt(),
                entity.getAcceptedAt(),
                entity.getDeclinedAt(),
                entity.getExpiredAt()
        );
    }

    private ProvisionalRepaymentItem toDomain(ApprovedOfferRepaymentItemJpaEntity entity) {
        return new ProvisionalRepaymentItem(
                entity.getId(),
                entity.getInstallmentNumber(),
                entity.getPrincipalDue(),
                entity.getInterestDue(),
                entity.getFeeDue(),
                entity.getTotalDue()
        );
    }
}
