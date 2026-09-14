package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.StaffApprovedSettlementEvidenceDto;
import com.meridian.platform.loan.application.port.in.QueryStaffApprovedSettlementEvidenceUseCase;
import com.meridian.platform.loan.application.port.out.ApprovedLoanSettlementRepository;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.RepaymentTransactionRepository;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentTransactionType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class QueryStaffApprovedSettlementEvidenceService
        implements QueryStaffApprovedSettlementEvidenceUseCase {

    private final ApprovedLoanSettlementRepository settlements;
    private final RepaymentTransactionRepository transactions;
    private final LoanAccountRepository accounts;
    private final CurrentUserProvider currentUserProvider;

    public QueryStaffApprovedSettlementEvidenceService(
            ApprovedLoanSettlementRepository settlements,
            RepaymentTransactionRepository transactions,
            LoanAccountRepository accounts,
            CurrentUserProvider currentUserProvider
    ) {
        this.settlements = settlements;
        this.transactions = transactions;
        this.accounts = accounts;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StaffApprovedSettlementEvidenceDto query(UUID loanApplicationId) {
        requireAuthority(currentUserProvider.currentUser());
        var account = accounts.findByLoanApplicationId(loanApplicationId)
                .orElseThrow(QueryStaffApprovedSettlementEvidenceService::notFound);
        var settlement = settlements.findByLoanAccountId(account.id())
                .orElseThrow(QueryStaffApprovedSettlementEvidenceService::notFound);
        var transaction = transactions.findById(settlement.repaymentTransactionId())
                .orElseThrow(QueryStaffApprovedSettlementEvidenceService::stateConflict);
        if (!settlement.loanApplicationId().equals(loanApplicationId)
                || !settlement.loanAccountId().equals(account.id())
                || !transaction.loanApplicationId().equals(loanApplicationId)
                || !transaction.loanAccountId().equals(account.id())
                || transaction.transactionType()
                != RepaymentTransactionType.APPROVED_SETTLEMENT
                || !transaction.requestId().equals(settlement.requestId())
                || transaction.receivedAmount().compareTo(settlement.settlementAmount()) != 0
                || !transaction.recordedByUserId().equals(settlement.approvedByUserId())
                || !ServicingEvidenceTimestamp.same(transaction.recordedAt(), settlement.approvedAt())
                || (account.status() != LoanAccountStatus.SETTLED
                && account.status() != LoanAccountStatus.CLOSED)) {
            throw stateConflict();
        }
        return new StaffApprovedSettlementEvidenceDto(
                loanApplicationId,
                account.id(),
                settlement.settlementAmount(),
                transaction.paymentValueDate(),
                settlement.approvedAt()
        );
    }

    private static void requireAuthority(AuthenticatedUser actor) {
        if (actor == null
                || !"STAFF".equals(actor.userType())
                || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission("loan:settlement:approve")
                || !actor.roles().contains("APPROVER")) {
            throw new AuthorizationException(
                    "LOAN_SETTLEMENT_EVIDENCE_ACCESS_DENIED",
                    "Approver settlement evidence access is denied."
            );
        }
    }

    private static EntityNotFoundException notFound() {
        return new EntityNotFoundException(
                "APPROVED_LOAN_SETTLEMENT_NOT_FOUND",
                "Approved Loan Settlement was not found."
        );
    }

    private static BusinessStateConflictException stateConflict() {
        return new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT",
                "Approved Loan Settlement evidence is inconsistent."
        );
    }
}
