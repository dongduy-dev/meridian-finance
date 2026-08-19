package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.Collateral;

import java.util.List;
import java.util.UUID;

public interface CollateralRepository {

    Collateral save(Collateral collateral);

    List<Collateral> findByLoanApplicationId(UUID loanApplicationId);
}
