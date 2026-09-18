package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.AssistedOriginationCaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaAssistedOriginationCaseRepository
        extends JpaRepository<AssistedOriginationCaseJpaEntity, UUID> {

    List<AssistedOriginationCaseJpaEntity> findAllByOrderByUpdatedAtDescIdDesc();

    List<AssistedOriginationCaseJpaEntity> findAllByStatusOrderByUpdatedAtDescIdDesc(
            AssistedOriginationCaseStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assisted from AssistedOriginationCaseJpaEntity assisted where assisted.id = :caseId")
    Optional<AssistedOriginationCaseJpaEntity> findByIdForUpdate(@Param("caseId") UUID caseId);
}
