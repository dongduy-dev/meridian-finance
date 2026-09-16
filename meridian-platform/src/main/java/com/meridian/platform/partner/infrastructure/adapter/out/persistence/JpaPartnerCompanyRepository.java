package com.meridian.platform.partner.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaPartnerCompanyRepository extends JpaRepository<PartnerCompanyJpaEntity, UUID> {

    List<PartnerCompanyJpaEntity> findAllByOrderByCompanyCodeAsc();

    boolean existsByCompanyCode(String companyCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select company from PartnerCompanyJpaEntity company where company.id = :partnerCompanyId")
    Optional<PartnerCompanyJpaEntity> findByIdForUpdate(@Param("partnerCompanyId") UUID partnerCompanyId);
}
