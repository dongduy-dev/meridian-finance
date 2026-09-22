package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaAssistedActionDocumentRepository extends JpaRepository<AssistedActionDocumentJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from AssistedActionDocumentJpaEntity document "
            + "where document.loanApplicationId = :applicationId and document.approvedOfferId = :offerId")
    Optional<AssistedActionDocumentJpaEntity> findOfferForUpdate(
            @Param("applicationId") UUID applicationId, @Param("offerId") UUID offerId);

    Optional<AssistedActionDocumentJpaEntity> findByLoanApplicationIdAndApprovedOfferId(
            UUID applicationId, UUID offerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from AssistedActionDocumentJpaEntity document "
            + "where document.loanApplicationId = :applicationId and document.loanContractId = :contractId "
            + "and document.contractVersion = :version")
    Optional<AssistedActionDocumentJpaEntity> findContractForUpdate(
            @Param("applicationId") UUID applicationId,
            @Param("contractId") UUID contractId,
            @Param("version") int version);

    Optional<AssistedActionDocumentJpaEntity> findByLoanApplicationIdAndLoanContractIdAndContractVersion(
            UUID applicationId, UUID contractId, int version);
}
