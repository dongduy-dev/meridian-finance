package com.meridian.platform.testsupport.architecture.loan.infrastructure.adapter.in.approval;

public abstract class RepositoryLeak implements com.meridian.platform.approval.application.spi.loan.ApprovalLoanReviewCycleSpi {
    private com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository repository;
}
