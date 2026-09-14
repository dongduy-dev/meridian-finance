package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.ApprovedLoanSettlementRepository;
import com.meridian.platform.loan.application.port.out.LoanAccountRepository;
import com.meridian.platform.loan.application.port.out.RepaymentTransactionRepository;
import com.meridian.platform.loan.domain.model.ApprovedLoanSettlement;
import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.RepaymentTransaction;
import com.meridian.platform.loan.domain.model.RepaymentTransactionType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryStaffApprovedSettlementEvidenceServiceTest {
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID TRANSACTION_ID = UUID.randomUUID();
    private static final UUID APPROVER_ID = UUID.randomUUID();
    private static final LocalDate VALUE_DATE = LocalDate.of(2026, 9, 14);
    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 9, 14, 8, 0);

    @Mock ApprovedLoanSettlementRepository settlements;
    @Mock RepaymentTransactionRepository transactions;
    @Mock LoanAccountRepository accounts;
    @Mock CurrentUserProvider users;
    @Mock LoanAccount account;
    @Mock ApprovedLoanSettlement settlement;
    @Mock RepaymentTransaction transaction;

    private QueryStaffApprovedSettlementEvidenceService service;

    @BeforeEach
    void setUp() {
        service = new QueryStaffApprovedSettlementEvidenceService(
                settlements, transactions, accounts, users
        );
    }

    @Test
    void returnsOnlyPurposeLimitedImmutableEvidenceForAnAuthorizedApprover() {
        arrangeCoherentEvidence();

        var result = service.query(APPLICATION_ID);

        assertEquals(APPLICATION_ID, result.loanApplicationId());
        assertEquals(ACCOUNT_ID, result.loanAccountId());
        assertEquals(new BigDecimal("1100"), result.settlementAmount());
        assertEquals(VALUE_DATE, result.paymentValueDate());
        assertEquals(APPROVED_AT, result.approvedAt());
    }

    @Test
    void deniesAStaffActorWithoutTheApproverRoleBeforeReadingEvidence() {
        when(users.currentUser()).thenReturn(actor(Set.of("ACCOUNTING_OFFICER")));

        assertThrows(AuthorizationException.class, () -> service.query(APPLICATION_ID));

        verify(accounts, never()).findByLoanApplicationId(APPLICATION_ID);
    }

    @Test
    void rejectsATransactionThatIsNotSettlementEvidence() {
        when(users.currentUser()).thenReturn(actor(Set.of("APPROVER")));
        when(accounts.findByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.of(account));
        when(account.id()).thenReturn(ACCOUNT_ID);
        when(settlements.findByLoanAccountId(ACCOUNT_ID)).thenReturn(Optional.of(settlement));
        when(settlement.loanApplicationId()).thenReturn(APPLICATION_ID);
        when(settlement.loanAccountId()).thenReturn(ACCOUNT_ID);
        when(settlement.repaymentTransactionId()).thenReturn(TRANSACTION_ID);
        when(transactions.findById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));
        when(transaction.loanApplicationId()).thenReturn(APPLICATION_ID);
        when(transaction.loanAccountId()).thenReturn(ACCOUNT_ID);
        when(transaction.transactionType()).thenReturn(RepaymentTransactionType.REPAYMENT);

        assertEquals("SYSTEM_STATE_CONFLICT", assertThrows(
                BusinessStateConflictException.class,
                () -> service.query(APPLICATION_ID)
        ).getErrorCode());
    }

    private void arrangeCoherentEvidence() {
        when(users.currentUser()).thenReturn(actor(Set.of("APPROVER")));
        when(accounts.findByLoanApplicationId(APPLICATION_ID)).thenReturn(Optional.of(account));
        when(account.id()).thenReturn(ACCOUNT_ID);
        when(account.status()).thenReturn(LoanAccountStatus.SETTLED);
        when(settlements.findByLoanAccountId(ACCOUNT_ID)).thenReturn(Optional.of(settlement));
        when(settlement.loanApplicationId()).thenReturn(APPLICATION_ID);
        when(settlement.loanAccountId()).thenReturn(ACCOUNT_ID);
        when(settlement.repaymentTransactionId()).thenReturn(TRANSACTION_ID);
        when(settlement.requestId()).thenReturn(REQUEST_ID);
        when(settlement.settlementAmount()).thenReturn(new BigDecimal("1100"));
        when(settlement.approvedByUserId()).thenReturn(APPROVER_ID);
        when(settlement.approvedAt()).thenReturn(APPROVED_AT);
        when(transactions.findById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));
        when(transaction.loanApplicationId()).thenReturn(APPLICATION_ID);
        when(transaction.loanAccountId()).thenReturn(ACCOUNT_ID);
        when(transaction.transactionType()).thenReturn(RepaymentTransactionType.APPROVED_SETTLEMENT);
        when(transaction.requestId()).thenReturn(REQUEST_ID);
        when(transaction.receivedAmount()).thenReturn(new BigDecimal("1100"));
        when(transaction.paymentValueDate()).thenReturn(VALUE_DATE);
        when(transaction.recordedByUserId()).thenReturn(APPROVER_ID);
        when(transaction.recordedAt()).thenReturn(APPROVED_AT);
    }

    private static AuthenticatedUser actor(Set<String> roles) {
        return new AuthenticatedUser(
                APPROVER_ID, "approver@meridian.test", "STAFF", null,
                roles, Set.of("loan:settlement:approve")
        );
    }
}
