package com.meridian.platform.testsupport.architecture.approval.application.spi.loan;

public interface InternalTypeLeak {
    java.util.Optional<Result> query();
    record Result(java.util.List<java.util.Optional<com.meridian.platform.loan.domain.model.LoanApplication>> applications) {}
}
