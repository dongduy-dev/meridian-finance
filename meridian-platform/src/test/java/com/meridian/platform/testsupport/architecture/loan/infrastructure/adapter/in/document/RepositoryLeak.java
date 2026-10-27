package com.meridian.platform.testsupport.architecture.loan.infrastructure.adapter.in.document;

public class RepositoryLeak implements com.meridian.platform.document.application.spi.loan.LoanDocumentWorkflowSpi {
 com.meridian.platform.loan.application.port.out.LoanApplicationRepository repository;
 public LoanDocumentWorkflowSnapshot find(java.util.UUID id) { return null; }
 public LoanDocumentWorkflowSnapshot lock(java.util.UUID id) { return null; }
}
