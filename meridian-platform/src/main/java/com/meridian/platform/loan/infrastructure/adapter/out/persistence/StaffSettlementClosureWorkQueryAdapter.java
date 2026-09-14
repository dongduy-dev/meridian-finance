package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.StaffSettlementClosureWorkQuery;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class StaffSettlementClosureWorkQueryAdapter
        implements StaffSettlementClosureWorkQuery {

    private final JpaLoanAccountRepository loanAccounts;

    public StaffSettlementClosureWorkQueryAdapter(JpaLoanAccountRepository loanAccounts) {
        this.loanAccounts = loanAccounts;
    }

    @Override
    public Page findSettlementPage(ProductCode productCode, int page, int size) {
        return toPage(loanAccounts.findStaffSettlementWork(
                productCode == null ? null : productCode.name(),
                PageRequest.of(page, size)
        ));
    }

    @Override
    public Page findClosurePage(ProductCode productCode, int page, int size) {
        return toPage(loanAccounts.findStaffClosureWork(
                productCode == null ? null : productCode.name(),
                PageRequest.of(page, size)
        ));
    }

    private static Page toPage(
            org.springframework.data.domain.Page<
                    JpaLoanAccountRepository.StaffSettlementClosureWorkProjection> selected
    ) {
        return new Page(
                selected.getNumber(), selected.getSize(), selected.getTotalElements(),
                selected.getTotalPages(), selected.getContent().stream()
                .map(StaffSettlementClosureWorkQueryAdapter::toRow).toList()
        );
    }

    private static Row toRow(
            JpaLoanAccountRepository.StaffSettlementClosureWorkProjection item
    ) {
        return new Row(
                item.getLoanApplicationId(), item.getLoanAccountId(),
                item.getApplicationNumber(), item.getAccountNumber(),
                ProductCode.valueOf(item.getProductCode()),
                ProductType.valueOf(item.getProductType()),
                LoanApplicationStatus.valueOf(item.getApplicationStatus()),
                LoanAccountStatus.valueOf(item.getAccountStatus()),
                item.getActivatedAt(), item.getTotalPaid(), item.getTotalOutstanding(),
                item.getServicingEvaluationDate(), item.getLastPaymentValueDate(),
                item.getLastPaymentRecordedAt(), item.getEvidenceCoherent(),
                item.getPayoffProvenance()
        );
    }
}
