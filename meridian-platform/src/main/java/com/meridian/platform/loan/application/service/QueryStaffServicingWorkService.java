package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.StaffServicingWorkPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffServicingWorkUseCase;
import com.meridian.platform.loan.application.port.out.StaffLoanAccountWorkQuery;
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

import java.util.EnumSet;

@Service
public class QueryStaffServicingWorkService implements QueryStaffServicingWorkUseCase {

    public static final int MAX_PAGE_SIZE = 100;
    private static final EnumSet<LoanAccountStatus> SERVICEABLE_STATUSES = EnumSet.of(
            LoanAccountStatus.ACTIVE,
            LoanAccountStatus.OVERDUE
    );

    private final StaffLoanAccountWorkQuery workQuery;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffServicingWorkService(
            StaffLoanAccountWorkQuery workQuery,
            CurrentUserProvider currentUserProvider
    ) {
        this.workQuery = workQuery;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffServicingWorkPageDto queryWork(
            ProductCode productCode,
            LoanAccountStatus accountStatus,
            int page,
            int size
    ) {
        requireAuthority(currentUserProvider.currentUser());
        requireValidFilters(accountStatus, page, size);
        StaffLoanAccountWorkQuery.Page selected = workQuery.findPage(
                productCode,
                accountStatus,
                page,
                size
        );
        return new StaffServicingWorkPageDto(
                selected.page(),
                selected.size(),
                selected.totalElements(),
                selected.totalPages(),
                selected.rows().stream().map(QueryStaffServicingWorkService::toItem).toList()
        );
    }

    private static StaffServicingWorkPageDto.ItemDto toItem(
            StaffLoanAccountWorkQuery.Row row
    ) {
        if (row.applicationStatus() != LoanApplicationStatus.DISBURSED
                || !SERVICEABLE_STATUSES.contains(row.accountStatus())
                || row.totalOutstanding() == null
                || row.totalOutstanding().signum() <= 0) {
            throw systemConflict();
        }
        return new StaffServicingWorkPageDto.ItemDto(
                row.loanApplicationId(),
                row.loanAccountId(),
                row.applicationNumber(),
                row.accountNumber(),
                row.productCode().name(),
                row.productType().name(),
                row.accountStatus().name(),
                row.activatedAt(),
                row.originatedPrincipal(),
                row.totalPaid(),
                row.totalOutstanding(),
                row.servicingEvaluationDate(),
                row.lastPaymentValueDate(),
                row.lastPaymentRecordedAt()
        );
    }

    private static void requireAuthority(AuthenticatedUser actor) {
        if (actor == null
                || !"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:read")) {
            throw new AuthorizationException(
                    "LOAN_SERVICING_WORK_ACCESS_DENIED",
                    "Staff Loan Account servicing work access is denied."
            );
        }
    }

    private static void requireValidFilters(
            LoanAccountStatus accountStatus,
            int page,
            int size
    ) {
        if ((accountStatus != null && !SERVICEABLE_STATUSES.contains(accountStatus))
                || page < 0
                || size < 1
                || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Servicing work query arguments are invalid.");
        }
    }

    private static BusinessStateConflictException systemConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Loan Account servicing work evidence is inconsistent."
        );
    }
}
