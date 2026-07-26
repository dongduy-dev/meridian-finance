package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanContractRepaymentItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "loan_contract_repayment_items")
public class LoanContractRepaymentItemJpaEntity {
    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "loan_contract_id", nullable = false) private UUID loanContractId;
    @Column(name = "source_approved_offer_repayment_item_id", nullable = false) private UUID sourceApprovedOfferRepaymentItemId;
    @Column(name = "installment_number", nullable = false) private int installmentNumber;
    @Column(name = "principal_due", nullable = false) private BigDecimal principalDue;
    @Column(name = "interest_due", nullable = false) private BigDecimal interestDue;
    @Column(name = "fee_due", nullable = false) private BigDecimal feeDue;
    @Column(name = "total_due", nullable = false) private BigDecimal totalDue;

    protected LoanContractRepaymentItemJpaEntity() {}
    LoanContractRepaymentItemJpaEntity(UUID contractId, LoanContractRepaymentItem item) {
        id = item.id(); loanContractId = contractId;
        sourceApprovedOfferRepaymentItemId = item.sourceApprovedOfferRepaymentItemId();
        installmentNumber = item.installmentNumber(); principalDue = item.principalDue();
        interestDue = item.interestDue(); feeDue = item.feeDue(); totalDue = item.totalDue();
    }
    LoanContractRepaymentItem toDomain() {
        return new LoanContractRepaymentItem(id, sourceApprovedOfferRepaymentItemId, installmentNumber,
                principalDue, interestDue, feeDue, totalDue);
    }
}
