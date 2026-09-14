package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.StaffClosureWorkPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffClosureWorkUseCase;
import com.meridian.platform.loan.application.port.out.StaffSettlementClosureWorkQuery;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryStaffClosureWorkService implements QueryStaffClosureWorkUseCase {
    public static final int MAX_PAGE_SIZE = 100;

    private final StaffSettlementClosureWorkQuery workQuery;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffClosureWorkService(
            StaffSettlementClosureWorkQuery workQuery,
            CurrentUserProvider currentUserProvider
    ) {
        this.workQuery = workQuery;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffClosureWorkPageDto queryWork(ProductCode productCode, int page, int size) {
        requireAuthority(currentUserProvider.currentUser());
        requireValidPaging(page, size);
        StaffSettlementClosureWorkQuery.Page selected = workQuery.findClosurePage(
                productCode, page, size
        );
        return new StaffClosureWorkPageDto(
                selected.page(), selected.size(), selected.totalElements(), selected.totalPages(),
                selected.rows().stream().map(QueryStaffClosureWorkService::toItem).toList()
        );
    }

    private static StaffClosureWorkPageDto.ItemDto toItem(
            StaffSettlementClosureWorkQuery.Row row
    ) {
        if (row.applicationStatus() != LoanApplicationStatus.DISBURSED
                || row.accountStatus() != LoanAccountStatus.SETTLED
                || row.totalOutstanding() == null
                || row.totalOutstanding().signum() != 0
                || !row.evidenceCoherent()
                || row.payoffProvenance() == null) {
            throw systemConflict();
        }
        return new StaffClosureWorkPageDto.ItemDto(
                row.loanApplicationId(), row.loanAccountId(), row.applicationNumber(),
                row.accountNumber(), row.productCode().name(), row.productType().name(),
                row.accountStatus().name(), row.activatedAt(), row.totalPaid(),
                row.totalOutstanding(), row.servicingEvaluationDate(),
                row.lastPaymentValueDate(), row.lastPaymentRecordedAt(),
                row.payoffProvenance()
        );
    }

    private static void requireAuthority(AuthenticatedUser actor) {
        if (actor == null
                || !"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:account:close")
                || !actor.roles().contains("ACCOUNTING_OFFICER")) {
            throw new AuthorizationException(
                    "LOAN_CLOSURE_WORK_ACCESS_DENIED",
                    "Accounting Officer closure work access is denied."
            );
        }
    }

    private static void requireValidPaging(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Closure work query arguments are invalid.");
        }
    }

    private static BusinessStateConflictException systemConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Closure work evidence is inconsistent."
        );
    }
}
