package com.meridian.platform.loan.application.mapper;

import com.meridian.platform.loan.application.dto.ContractReadinessDto;
import com.meridian.platform.loan.application.dto.LoanContractBankAccountDto;
import com.meridian.platform.loan.application.dto.LoanContractDto;
import com.meridian.platform.loan.application.dto.LoanContractRepaymentItemDto;
import com.meridian.platform.loan.application.port.in.QueryContractReadinessUseCase;
import com.meridian.platform.loan.domain.model.ApprovedOfferFinancialTerms;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.loan.domain.model.ProtectedDisbursementBankAccount;
import org.springframework.stereotype.Component;

@Component
public class LoanContractMapper {

    public LoanContractDto toDto(LoanContract contract) {
        ApprovedOfferFinancialTerms terms = contract.financialTerms();
        ProtectedDisbursementBankAccount account = contract.disbursementBankAccount();
        return new LoanContractDto(
                contract.id(),
                contract.contractReference(),
                contract.contractVersion(),
                contract.status().name(),
                terms.approvedPrincipal(),
                terms.approvedTermMonths(),
                terms.interestCalculationMethod().name(),
                terms.flatMonthlyInterestRate(),
                terms.totalInterest(),
                terms.feeAmount(),
                terms.totalRepaymentAmount(),
                terms.repaymentMethod().name(),
                contract.repaymentItems().stream()
                        .map(item -> new LoanContractRepaymentItemDto(
                                item.installmentNumber(),
                                item.principalDue(),
                                item.interestDue(),
                                item.feeDue(),
                                item.totalDue()
                        ))
                        .toList(),
                new LoanContractBankAccountDto(
                        account.bankCode(),
                        account.bankNameSnapshot(),
                        account.accountHolderName(),
                        "****" + account.lastFour(),
                        account.primaryAtCapture(),
                        account.activeAtCapture(),
                        account.capturedAt()
                ),
                contract.preparedAt(),
                contract.acknowledgedAt(),
                contract.confirmedAt(),
                contract.status() == LoanContractStatus.PREPARED ? "ACKNOWLEDGE" : null
        );
    }

    public ContractReadinessDto toDto(QueryContractReadinessUseCase.Snapshot snapshot) {
        return new ContractReadinessDto(
                snapshot.loanApplicationId(),
                snapshot.contractId(),
                snapshot.contractVersion(),
                snapshot.ready(),
                snapshot.blockers().stream().map(Enum::name).toList(),
                "POINT_IN_TIME_ADVISORY",
                true
        );
    }
}
