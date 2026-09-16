package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.partner.application.service.ManagePartnerCompanyService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PartnerCompanyRepositoryAdapter implements PartnerCompanyRepository {

    private final JpaPartnerCompanyRepository jpaPartnerCompanyRepository;
    private final Clock clock;

    public PartnerCompanyRepositoryAdapter(JpaPartnerCompanyRepository jpaPartnerCompanyRepository, Clock clock) {
        this.jpaPartnerCompanyRepository = jpaPartnerCompanyRepository;
        this.clock = clock;
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

    @Override
    public Optional<PartnerCompany> findByIdForUpdate(UUID partnerCompanyId) {
        return jpaPartnerCompanyRepository.findByIdForUpdate(partnerCompanyId).map(this::toDomain);
    }

    @Override
    public boolean existsByCompanyCode(String companyCode) {
        return jpaPartnerCompanyRepository.existsByCompanyCode(companyCode);
    }

    @Override
    public PartnerCompany save(PartnerCompany partnerCompany) {
        LocalDateTime now = LocalDateTime.now(clock);
        PartnerCompanyJpaEntity entity = jpaPartnerCompanyRepository.findById(partnerCompany.id())
                .map(existing -> {
                    existing.update(
                            partnerCompany.name(), partnerCompany.status(),
                            partnerCompany.salaryAdvancePolicyLimit(), now
                    );
                    return existing;
                })
                .orElseGet(() -> new PartnerCompanyJpaEntity(
                        partnerCompany.id(), partnerCompany.companyCode(), partnerCompany.name(),
                        partnerCompany.status(), partnerCompany.salaryAdvancePolicyLimit(), now, now
                ));
        try {
            return toDomain(jpaPartnerCompanyRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw ManagePartnerCompanyService.companyCodeConflict();
        }
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
