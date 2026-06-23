package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class PartnerEmployeeRepositoryAdapter implements PartnerEmployeeRepository {

    private final JpaPartnerEmployeeRepository jpaPartnerEmployeeRepository;

    public PartnerEmployeeRepositoryAdapter(JpaPartnerEmployeeRepository jpaPartnerEmployeeRepository) {
        this.jpaPartnerEmployeeRepository = jpaPartnerEmployeeRepository;
    }

    @Override
    public List<PartnerEmployee> findByPartnerCompanyId(UUID partnerCompanyId) {
        return jpaPartnerEmployeeRepository.findByPartnerCompanyIdOrderByEmployeeCodeAsc(partnerCompanyId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PartnerEmployee> findActiveByPartnerCompanyId(UUID partnerCompanyId) {
        return jpaPartnerEmployeeRepository.findByPartnerCompanyIdAndActiveTrueOrderByEmployeeCodeAsc(partnerCompanyId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private PartnerEmployee toDomain(PartnerEmployeeJpaEntity entity) {
        return new PartnerEmployee(
                entity.getId(),
                entity.getPartnerCompanyId(),
                entity.getImportBatchId(),
                entity.getEmployeeCode(),
                entity.getIdentityReference(),
                entity.getSalaryAmount(),
                entity.getSalaryAdvanceLimit(),
                entity.getEmploymentStatus(),
                entity.isActive()
        );
    }
}
