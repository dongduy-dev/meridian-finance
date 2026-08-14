package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.Collateral;

public interface CollateralRepository {

    Collateral save(Collateral collateral);
}
