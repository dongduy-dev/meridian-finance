package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.StaffSettlementWorkPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffSettlementWorkUseCase;
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
public class QueryStaffSettlementWorkService implements QueryStaffSettlementWorkUseCase {
    public static final int MAX_PAGE_SIZE = 100;

    private final StaffSettlementClosureWorkQuery workQuery;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffSettlementWorkService(
            StaffSettlementClosureWorkQuery workQuery,
            CurrentUserProvider currentUserProvider
    ) {
        this.workQuery = workQuery;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffSettlementWorkPageDto queryWork(ProductCode productCode, int page, int size) {
        requireAuthority(currentUserProvider.currentUser());
        requireValidPaging(page, size);
        StaffSettlementClosureWorkQuery.Page selected = workQuery.findSettlementPage(
                productCode, page, size
        );
        return new StaffSettlementWorkPageDto(
                selected.page(), selected.size(), selected.totalElements(), selected.totalPages(),
                selected.rows().stream().map(QueryStaffSettlementWorkService::toItem).toList()
        );
    }

    private static StaffSettlementWorkPageDto.ItemDto toItem(
            StaffSettlementClosureWorkQuery.Row row
    ) {
        if (row.applicationStatus() != LoanApplicationStatus.DISBURSED
                || (row.accountStatus() != LoanAccountStatus.ACTIVE
                && row.accountStatus() != LoanAccountStatus.OVERDUE)
                || row.totalOutstanding() == null
                || row.totalOutstanding().signum() <= 0
                || !row.evidenceCoherent()) {
            throw systemConflict();
        }
        return new StaffSettlementWorkPageDto.ItemDto(
                row.loanApplicationId(), row.loanAccountId(), row.applicationNumber(),
                row.accountNumber(), row.productCode().name(), row.productType().name(),
                row.accountStatus().name(), row.activatedAt(), row.totalPaid(),
                row.totalOutstanding(), row.servicingEvaluationDate(),
                row.lastPaymentValueDate(), row.lastPaymentRecordedAt()
        );
    }

    private static void requireAuthority(AuthenticatedUser actor) {
        if (actor == null
                || !"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:settlement:approve")
                || !actor.roles().contains("APPROVER")) {
            throw new AuthorizationException(
                    "LOAN_SETTLEMENT_WORK_ACCESS_DENIED",
                    "Approver settlement work access is denied."
            );
        }
    }

    private static void requireValidPaging(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Settlement work query arguments are invalid.");
        }
    }

    private static BusinessStateConflictException systemConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Settlement work evidence is inconsistent."
        );
    }
}
