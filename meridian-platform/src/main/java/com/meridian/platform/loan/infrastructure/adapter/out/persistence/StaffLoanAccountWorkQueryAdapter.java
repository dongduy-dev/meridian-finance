package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.StaffLoanAccountWorkQuery;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.EnumSet;

@Repository
public class StaffLoanAccountWorkQueryAdapter implements StaffLoanAccountWorkQuery {

    private static final EnumSet<LoanAccountStatus> SERVICEABLE_STATUSES = EnumSet.of(
            LoanAccountStatus.ACTIVE,
            LoanAccountStatus.OVERDUE
    );

    private final JpaLoanAccountRepository loanAccounts;

    public StaffLoanAccountWorkQueryAdapter(JpaLoanAccountRepository loanAccounts) {
        this.loanAccounts = loanAccounts;
    }

    @Override
    public Page findPage(
            ProductCode productCode,
            LoanAccountStatus accountStatus,
            int page,
            int size
    ) {
        org.springframework.data.domain.Page<JpaLoanAccountRepository.StaffServicingWorkProjection>
                selected = accountStatus == null
                ? loanAccounts.findStaffServicingWork(
                        productCode,
                        SERVICEABLE_STATUSES,
                        PageRequest.of(page, size)
                )
                : loanAccounts.findStaffServicingWorkByStatus(
                        productCode,
                        accountStatus,
                        SERVICEABLE_STATUSES,
                        PageRequest.of(page, size)
                );
        return new Page(
                selected.getNumber(),
                selected.getSize(),
                selected.getTotalElements(),
                selected.getTotalPages(),
                selected.getContent().stream().map(StaffLoanAccountWorkQueryAdapter::toRow).toList()
        );
    }

    private static Row toRow(
            JpaLoanAccountRepository.StaffServicingWorkProjection item
    ) {
        return new Row(
                item.getLoanApplicationId(),
                item.getLoanAccountId(),
                item.getApplicationNumber(),
                item.getAccountNumber(),
                item.getProductCode(),
                item.getProductType(),
                item.getApplicationStatus(),
                item.getAccountStatus(),
                item.getActivatedAt(),
                item.getOriginatedPrincipal(),
                item.getTotalPaid(),
                item.getTotalOutstanding(),
                item.getServicingEvaluationDate(),
                item.getLastPaymentValueDate(),
                item.getLastPaymentRecordedAt()
        );
    }
}
