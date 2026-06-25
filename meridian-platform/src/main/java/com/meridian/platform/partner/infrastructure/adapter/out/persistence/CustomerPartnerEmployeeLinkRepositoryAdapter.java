package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.port.out.CustomerPartnerEmployeeLinkRepository;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerPartnerEmployeeLinkRepositoryAdapter implements CustomerPartnerEmployeeLinkRepository {

    private final JpaCustomerPartnerEmployeeLinkRepository jpaRepository;

    public CustomerPartnerEmployeeLinkRepositoryAdapter(JpaCustomerPartnerEmployeeLinkRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<CustomerPartnerEmployeeLink> findCurrentByCustomerIdAndPartnerCompanyId(
            UUID customerId,
            UUID partnerCompanyId
    ) {
        return jpaRepository.findFirstByCustomerIdAndPartnerCompanyIdOrderByUpdatedAtDesc(customerId, partnerCompanyId)
                .map(this::toDomain);
    }

    @Override
    public CustomerPartnerEmployeeLink save(CustomerPartnerEmployeeLink customerPartnerEmployeeLink) {
        CustomerPartnerEmployeeLinkJpaEntity entity = jpaRepository.findById(customerPartnerEmployeeLink.id())
                .map(existingEntity -> {
                    existingEntity.updateFrom(customerPartnerEmployeeLink);
                    return existingEntity;
                })
                .orElseGet(() -> new CustomerPartnerEmployeeLinkJpaEntity(customerPartnerEmployeeLink));

        return toDomain(jpaRepository.save(entity));
    }

    private CustomerPartnerEmployeeLink toDomain(CustomerPartnerEmployeeLinkJpaEntity entity) {
        return new CustomerPartnerEmployeeLink(
                entity.getId(),
                entity.getCustomerId(),
                entity.getPartnerCompanyId(),
                entity.getPartnerEmployeeId(),
                entity.getSourceImportBatchId(),
                entity.getVerificationOutcome(),
                entity.getLinkStatus(),
                entity.getVerifiedIdentityRef(),
                entity.getVerifiedEmployeeCode(),
                entity.getLastVerifiedAt(),
                entity.getLastRefreshedAt()
        );
    }
}
