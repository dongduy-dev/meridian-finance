package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.StaffAssistedContractAcknowledgment;

import java.util.Optional;
import java.util.UUID;

public interface StaffAssistedContractAcknowledgmentRepository {

    StaffAssistedContractAcknowledgment save(StaffAssistedContractAcknowledgment acknowledgment);

    Optional<StaffAssistedContractAcknowledgment> findByAcknowledgmentRequestId(UUID requestId);

    Optional<StaffAssistedContractAcknowledgment> findByLoanContractIdAndContractVersion(
            UUID loanContractId, int contractVersion);
}
