package com.meridian.platform.loan.application.dto;

import org.junit.jupiter.api.Test;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerServicingActorPrivacyTest {
    @Test void sharedLoanAccountAndRepaymentContractsHaveNoStaffActorFields() {
        assertEquals(Set.of("loanApplicationId", "loanAccountId", "accountNumber",
                "status", "activatedAt", "originatedPrincipal", "approvedTermMonths",
                "totalInterest", "totalFee", "totalRepayment", "servicing",
                "disbursementDestination", "finalRepaymentSchedule"),
                fields(LoanAccountDto.class));
        assertEquals(Set.of("repaymentTransactionId", "receivedAmount", "paymentValueDate",
                "recordedAt", "principalAllocated", "principalReleased",
                "resultingLoanAccountStatus", "accountBalance", "allocations",
                "affectedInstallments"), fields(RepaymentHistoryPageDto.ItemDto.class));
    }

    private static Set<String> fields(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }
}
