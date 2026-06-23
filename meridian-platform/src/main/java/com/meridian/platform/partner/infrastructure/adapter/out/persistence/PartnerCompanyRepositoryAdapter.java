package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PartnerCompanyRepositoryAdapter implements PartnerCompanyRepository {

    private final JpaPartnerCompanyRepository jpaPartnerCompanyRepository;

    public PartnerCompanyRepositoryAdapter(JpaPartnerCompanyRepository jpaPartnerCompanyRepository) {
        this.jpaPartnerCompanyRepository = jpaPartnerCompanyRepository;
    }

    @Override
    public List<PartnerCompany> findAll() {
        return jpaPartnerCompanyRepository.findAllByOrderByCompanyCodeAsc()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<PartnerCompany> findById(UUID partnerCompanyId){
        return jpaPartnerCompanyRepository.findById(partnerCompanyId)
                .map(this::toDomain);
    }

    private PartnerCompany toDomain(PartnerCompanyJpaEntity entity) {
        return new PartnerCompany(
                entity.getId(),
                entity.getCompanyCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getSalaryAdvancePolicyLimit()
        );
    }
}
