package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.ManualDisbursement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "manual_disbursements")
public class ManualDisbursementJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "loan_application_id", nullable = false, updatable = false)
    private UUID loanApplicationId;

    @Column(name = "loan_contract_id", nullable = false, updatable = false)
    private UUID loanContractId;

    @Column(name = "loan_account_id", nullable = false, updatable = false)
    private UUID loanAccountId;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "expected_contract_version", nullable = false, updatable = false)
    private int expectedContractVersion;

    @Column(name = "external_transfer_reference", nullable = false, length = 64, updatable = false)
    private String externalTransferReference;

    @Column(name = "disbursed_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal disbursedAmount;

    @Column(name = "disbursement_value_date", nullable = false, updatable = false)
    private LocalDate valueDate;

    @Column(name = "first_repayment_date", nullable = false, updatable = false)
    private LocalDate firstRepaymentDate;

    @Column(name = "confirmed_by_user_id", nullable = false, updatable = false)
    private UUID confirmedByUserId;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ManualDisbursementJpaEntity() {
    }

    public ManualDisbursementJpaEntity(ManualDisbursement manualDisbursement) {
        this.id = manualDisbursement.id();
        this.loanApplicationId = manualDisbursement.loanApplicationId();
        this.loanContractId = manualDisbursement.loanContractId();
        this.loanAccountId = manualDisbursement.loanAccountId();
        this.requestId = manualDisbursement.requestId();
        this.expectedContractVersion = manualDisbursement.expectedContractVersion();
        this.externalTransferReference = manualDisbursement.externalTransferReference();
        this.disbursedAmount = manualDisbursement.disbursedAmount();
        this.valueDate = manualDisbursement.valueDate();
        this.firstRepaymentDate = manualDisbursement.firstRepaymentDate();
        this.confirmedByUserId = manualDisbursement.confirmedByUserId();
        this.confirmedAt = manualDisbursement.confirmedAt();
    }

    public ManualDisbursement toDomain() {
        return new ManualDisbursement(
                id,
                loanApplicationId,
                loanContractId,
                loanAccountId,
                requestId,
                expectedContractVersion,
                externalTransferReference,
                disbursedAmount,
                valueDate,
                firstRepaymentDate,
                confirmedByUserId,
                confirmedAt
        );
    }

    @Override
    public String toString() {
        return "ManualDisbursementJpaEntity[id=" + id
                + ", loanApplicationId=" + loanApplicationId
                + ", loanContractId=" + loanContractId
                + ", loanAccountId=" + loanAccountId
                + ", transferEvidence=redacted]";
    }
}
