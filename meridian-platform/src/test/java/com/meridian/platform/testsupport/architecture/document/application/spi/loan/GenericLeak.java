package com.meridian.platform.testsupport.architecture.document.application.spi.loan;

public interface GenericLeak {
 java.util.Optional<Nested> find();
 record Nested(java.util.Map<String, java.util.List<com.meridian.platform.loan.domain.model.LoanApplication>> applications) { }
}
