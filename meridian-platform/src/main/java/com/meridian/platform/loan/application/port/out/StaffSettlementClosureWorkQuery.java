package com.meridian.platform.loan.application.port.out;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface StaffSettlementClosureWorkQuery {

    Page findSettlementPage(ProductCode productCode, int page, int size);

    Page findClosurePage(ProductCode productCode, int page, int size);

    record Page(int page, int size, long totalElements, int totalPages, List<Row> rows) {
        public Page {
            rows = List.copyOf(rows);
        }
    }

    record Row(
            UUID loanApplicationId,
            UUID loanAccountId,
            String applicationNumber,
            String accountNumber,
            ProductCode productCode,
            ProductType productType,
            LoanApplicationStatus applicationStatus,
            LoanAccountStatus accountStatus,
            LocalDateTime activatedAt,
            BigDecimal totalPaid,
            BigDecimal totalOutstanding,
            LocalDate servicingEvaluationDate,
            LocalDate lastPaymentValueDate,
            LocalDateTime lastPaymentRecordedAt,
            boolean evidenceCoherent,
            String payoffProvenance
    ) {
    }
}
