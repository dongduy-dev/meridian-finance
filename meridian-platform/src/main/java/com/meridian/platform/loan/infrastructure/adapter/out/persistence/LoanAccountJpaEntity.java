package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanAccount;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_accounts")
public class LoanAccountJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false, updatable = false)
    private UUID loanApplicationId;

    @Column(name = "loan_contract_id", nullable = false, updatable = false)
    private UUID loanContractId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "account_number", nullable = false, length = 35, updatable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LoanAccountStatus status;

    @Column(name = "approved_principal", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal approvedPrincipal;

    @Column(name = "approved_term_months", nullable = false, updatable = false)
    private int approvedTermMonths;

    @Column(name = "total_interest", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalInterest;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal feeAmount;

    @Column(name = "total_repayment_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalRepaymentAmount;

    @Column(name = "activated_at", nullable = false, updatable = false)
    private LocalDateTime activatedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private LocalDateTime updatedAt;

    protected LoanAccountJpaEntity() {
    }

    public LoanAccountJpaEntity(LoanAccount loanAccount) {
        this.id = loanAccount.id();
        this.loanApplicationId = loanAccount.loanApplicationId();
        this.loanContractId = loanAccount.loanContractId();
        this.customerId = loanAccount.customerId();
        this.accountNumber = loanAccount.accountNumber();
        this.status = loanAccount.status();
        this.approvedPrincipal = loanAccount.approvedPrincipal();
        this.approvedTermMonths = loanAccount.approvedTermMonths();
        this.totalInterest = loanAccount.totalInterest();
        this.feeAmount = loanAccount.feeAmount();
        this.totalRepaymentAmount = loanAccount.totalRepaymentAmount();
        this.activatedAt = loanAccount.activatedAt();
    }

    public LoanAccount toDomain() {
        return new LoanAccount(
                id,
                loanApplicationId,
                loanContractId,
                customerId,
                accountNumber,
                status,
                approvedPrincipal,
                approvedTermMonths,
                totalInterest,
                feeAmount,
                totalRepaymentAmount,
                activatedAt
        );
    }

    UUID id() {
        return id;
    }
}
