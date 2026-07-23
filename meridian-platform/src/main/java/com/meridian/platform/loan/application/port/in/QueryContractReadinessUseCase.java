package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.domain.model.ContractReadinessBlockerCode;
import java.util.List;
import java.util.UUID;

public interface QueryContractReadinessUseCase {
    Snapshot query(UUID loanApplicationId, int expectedContractVersion);
    record Snapshot(UUID loanApplicationId, UUID contractId, Integer contractVersion,
                    boolean ready, List<ContractReadinessBlockerCode> blockers) {
        public Snapshot { blockers = List.copyOf(blockers); }
    }
}
