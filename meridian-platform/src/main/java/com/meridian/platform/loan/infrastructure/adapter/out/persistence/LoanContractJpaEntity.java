package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.*;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_contracts")
public class LoanContractJpaEntity {
    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "loan_application_id", nullable = false) private UUID loanApplicationId;
    @Column(name = "approved_offer_id", nullable = false) private UUID approvedOfferId;
    @Column(name = "contract_reference", nullable = false) private String contractReference;
    @Column(name = "contract_version", nullable = false) private int contractVersion;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private LoanContractStatus status;
    @Column(name = "approved_principal", nullable = false) private BigDecimal approvedPrincipal;
    @Column(name = "approved_term_months", nullable = false) private int approvedTermMonths;
    @Enumerated(EnumType.STRING) @Column(name = "interest_calculation_method", nullable = false) private InterestCalculationMethod interestCalculationMethod;
    @Column(name = "flat_monthly_interest_rate", nullable = false) private BigDecimal flatMonthlyInterestRate;
    @Column(name = "total_interest", nullable = false) private BigDecimal totalInterest;
    @Column(name = "fee_amount", nullable = false) private BigDecimal feeAmount;
    @Column(name = "total_repayment_amount", nullable = false) private BigDecimal totalRepaymentAmount;
    @Enumerated(EnumType.STRING) @Column(name = "repayment_method", nullable = false) private RepaymentMethod repaymentMethod;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "source_bank_account_id", nullable = false) private UUID sourceBankAccountId;
    @Column(name = "bank_code", nullable = false) private String bankCode;
    @Column(name = "bank_name_snapshot", nullable = false) private String bankNameSnapshot;
    @Column(name = "account_holder_name", nullable = false) private String accountHolderName;
    @Column(name = "account_number_last_four", nullable = false) private String accountNumberLastFour;
    @Column(name = "primary_at_capture", nullable = false) private boolean primaryAtCapture;
    @Column(name = "active_at_capture", nullable = false) private boolean activeAtCapture;
    @Column(name = "account_captured_at", nullable = false) private LocalDateTime accountCapturedAt;
    @Column(name = "protection_scheme", nullable = false) private String protectionScheme;
    @Column(name = "protection_key_id", nullable = false) private String protectionKeyId;
    @Column(name = "protection_nonce", nullable = false) private byte[] protectionNonce;
    @Column(name = "protected_account_number", nullable = false) private byte[] protectedAccountNumber;
    @Column(name = "protection_aad_version", nullable = false) private String protectionAadVersion;
    @Column(name = "preparation_request_id", nullable = false) private UUID preparationRequestId;
    @Column(name = "expected_previous_contract_version") private Integer expectedPreviousContractVersion;
    @Enumerated(EnumType.STRING) @Column(name = "supersession_reason") private ContractSupersessionReason supersessionReason;
    @Column(name = "prepared_by_user_id", nullable = false) private UUID preparedByUserId;
    @Column(name = "prepared_at", nullable = false) private LocalDateTime preparedAt;
    @Column(name = "acknowledgment_request_id") private UUID acknowledgmentRequestId;
    @Column(name = "acknowledged_by_user_id") private UUID acknowledgedByUserId;
    @Column(name = "acknowledged_at") private LocalDateTime acknowledgedAt;
    @Column(name = "confirmation_request_id") private UUID confirmationRequestId;
    @Column(name = "confirmed_by_user_id") private UUID confirmedByUserId;
    @Column(name = "confirmed_at") private LocalDateTime confirmedAt;
    @Column(name = "supersedes_contract_id") private UUID supersedesContractId;
    @Column(name = "superseded_by_user_id") private UUID supersededByUserId;
    @Column(name = "superseded_at") private LocalDateTime supersededAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected LoanContractJpaEntity() {}
    LoanContractJpaEntity(LoanContract contract) { applyImmutable(contract); updateLifecycle(contract); updatedAt = contract.preparedAt(); }
    void updateLifecycle(LoanContract contract) {
        status = contract.status(); acknowledgmentRequestId = contract.acknowledgmentRequestId();
        acknowledgedByUserId = contract.acknowledgedByUserId(); acknowledgedAt = contract.acknowledgedAt();
        confirmationRequestId = contract.confirmationRequestId(); confirmedByUserId = contract.confirmedByUserId();
        confirmedAt = contract.confirmedAt(); supersededByUserId = contract.supersededByUserId();
        supersededAt = contract.supersededAt(); updatedAt = latestTime(contract);
    }
    private void applyImmutable(LoanContract contract) {
        id = contract.id(); loanApplicationId = contract.loanApplicationId(); approvedOfferId = contract.approvedOfferId();
        contractReference = contract.contractReference(); contractVersion = contract.contractVersion();
        ApprovedOfferFinancialTerms terms = contract.financialTerms(); approvedPrincipal = terms.approvedPrincipal();
        approvedTermMonths = terms.approvedTermMonths(); interestCalculationMethod = terms.interestCalculationMethod();
        flatMonthlyInterestRate = terms.flatMonthlyInterestRate(); totalInterest = terms.totalInterest();
        feeAmount = terms.feeAmount(); totalRepaymentAmount = terms.totalRepaymentAmount(); repaymentMethod = terms.repaymentMethod();
        ProtectedDisbursementBankAccount account = contract.disbursementBankAccount(); customerId = account.customerId();
        sourceBankAccountId = account.sourceBankAccountId(); bankCode = account.bankCode(); bankNameSnapshot = account.bankNameSnapshot();
        accountHolderName = account.accountHolderName(); accountNumberLastFour = account.lastFour();
        primaryAtCapture = account.primaryAtCapture(); activeAtCapture = account.activeAtCapture(); accountCapturedAt = account.capturedAt();
        protectionScheme = account.protectionScheme(); protectionKeyId = account.keyId(); protectionNonce = account.nonce();
        protectedAccountNumber = account.ciphertext(); protectionAadVersion = account.aadVersion();
        preparationRequestId = contract.preparationRequestId(); expectedPreviousContractVersion = contract.expectedPreviousVersion();
        supersessionReason = contract.supersessionReason(); preparedByUserId = contract.preparedByUserId(); preparedAt = contract.preparedAt();
        supersedesContractId = contract.supersedesContractId();
    }
    private static LocalDateTime latestTime(LoanContract contract) {
        if (contract.confirmedAt() != null) return contract.confirmedAt();
        if (contract.supersededAt() != null) return contract.supersededAt();
        if (contract.acknowledgedAt() != null) return contract.acknowledgedAt();
        return contract.preparedAt();
    }
    UUID id() { return id; }
    LoanContract toDomain(java.util.List<LoanContractRepaymentItem> items) {
        return new LoanContract(id, loanApplicationId, approvedOfferId, contractReference, contractVersion, status,
                new ApprovedOfferFinancialTerms(approvedPrincipal, approvedTermMonths, interestCalculationMethod,
                        flatMonthlyInterestRate, totalInterest, feeAmount, totalRepaymentAmount, repaymentMethod), items,
                new ProtectedDisbursementBankAccount(customerId, sourceBankAccountId, bankCode, bankNameSnapshot,
                        accountHolderName, accountNumberLastFour, primaryAtCapture, activeAtCapture, accountCapturedAt,
                        protectionScheme, protectionKeyId, protectionNonce, protectedAccountNumber, protectionAadVersion),
                preparationRequestId, expectedPreviousContractVersion, supersessionReason, preparedByUserId, preparedAt,
                acknowledgmentRequestId, acknowledgedByUserId, acknowledgedAt, confirmationRequestId,
                confirmedByUserId, confirmedAt, supersedesContractId, supersededByUserId, supersededAt);
    }
}
