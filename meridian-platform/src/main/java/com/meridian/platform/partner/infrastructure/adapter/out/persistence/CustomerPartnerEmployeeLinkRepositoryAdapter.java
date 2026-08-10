package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.port.out.CustomerPartnerEmployeeLinkRepository;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLinkStatus;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerPartnerEmployeeLinkRepositoryAdapter implements CustomerPartnerEmployeeLinkRepository {

    private final JpaCustomerPartnerEmployeeLinkRepository jpaRepository;
    private final Clock clock;

    public CustomerPartnerEmployeeLinkRepositoryAdapter(
            JpaCustomerPartnerEmployeeLinkRepository jpaRepository,
            Clock clock
    ) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public Optional<CustomerPartnerEmployeeLink> findById(UUID customerPartnerEmployeeLinkId) {
        return jpaRepository.findById(customerPartnerEmployeeLinkId)
                .map(this::toDomain);
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
    public List<CustomerPartnerEmployeeLink> findByCustomerId(UUID customerId) {
        return jpaRepository.findByCustomerIdAndLinkStatusOrderByLastRefreshedAtDescIdAsc(
                        customerId,
                        CustomerPartnerEmployeeLinkStatus.VERIFIED
                ).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public CustomerPartnerEmployeeLink save(CustomerPartnerEmployeeLink customerPartnerEmployeeLink) {
        LocalDateTime now = LocalDateTime.now(clock);
        CustomerPartnerEmployeeLinkJpaEntity entity = jpaRepository.findById(customerPartnerEmployeeLink.id())
                .map(existingEntity -> {
                    existingEntity.updateFrom(customerPartnerEmployeeLink, now);
                    return existingEntity;
                })
                .orElseGet(() -> new CustomerPartnerEmployeeLinkJpaEntity(
                        customerPartnerEmployeeLink,
                        now
                ));

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
