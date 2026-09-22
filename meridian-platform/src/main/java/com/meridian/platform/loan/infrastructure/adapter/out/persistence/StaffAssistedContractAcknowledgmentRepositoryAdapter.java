package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.StaffAssistedContractAcknowledgmentRepository;
import com.meridian.platform.loan.domain.model.StaffAssistedContractAcknowledgment;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class StaffAssistedContractAcknowledgmentRepositoryAdapter
        implements StaffAssistedContractAcknowledgmentRepository {
    private final JpaStaffAssistedContractAcknowledgmentRepository acknowledgments;

    public StaffAssistedContractAcknowledgmentRepositoryAdapter(
            JpaStaffAssistedContractAcknowledgmentRepository acknowledgments
    ) {
        this.acknowledgments = acknowledgments;
    }
    @Override public StaffAssistedContractAcknowledgment save(StaffAssistedContractAcknowledgment value) {
        return acknowledgments.save(new StaffAssistedContractAcknowledgmentJpaEntity(value)).toDomain();
    }
    @Override public Optional<StaffAssistedContractAcknowledgment> findByAcknowledgmentRequestId(UUID requestId) {
        return acknowledgments.findByAcknowledgmentRequestId(requestId)
                .map(StaffAssistedContractAcknowledgmentJpaEntity::toDomain);
    }
    @Override public Optional<StaffAssistedContractAcknowledgment> findByLoanContractIdAndContractVersion(
            UUID contractId, int version) {
        return acknowledgments.findByLoanContractIdAndContractVersion(contractId, version)
                .map(StaffAssistedContractAcknowledgmentJpaEntity::toDomain);
    }
}
