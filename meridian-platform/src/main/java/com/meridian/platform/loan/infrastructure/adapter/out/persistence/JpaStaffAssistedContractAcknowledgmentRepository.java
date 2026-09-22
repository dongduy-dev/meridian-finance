package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaStaffAssistedContractAcknowledgmentRepository
        extends JpaRepository<StaffAssistedContractAcknowledgmentJpaEntity, UUID> {
    Optional<StaffAssistedContractAcknowledgmentJpaEntity> findByAcknowledgmentRequestId(UUID requestId);
    Optional<StaffAssistedContractAcknowledgmentJpaEntity> findByLoanContractIdAndContractVersion(
            UUID loanContractId, int contractVersion);
}
