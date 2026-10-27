package com.meridian.platform.testsupport.architecture.document.application.port.in;

import com.meridian.platform.loan.domain.model.LoanApplication;

import java.util.List;

public interface InternalTypeLeak {
    Envelope find();

    record Envelope(List<LoanApplication> applications) { }
}
