package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.partner.domain.port.out.PartnerCompanyRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

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