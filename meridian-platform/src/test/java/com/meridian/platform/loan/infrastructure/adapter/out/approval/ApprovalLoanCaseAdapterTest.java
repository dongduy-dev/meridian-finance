package com.meridian.platform.loan.infrastructure.adapter.out.approval;

import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanReviewCycleRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceVerificationRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanVerificationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalLoanCaseAdapterTest {

    @Mock LoanApplicationRepository applications;
    @Mock SalaryAdvanceVerificationRepository salaryAdvanceVerifications;
    @Mock UnsecuredConsumerLoanVerificationRepository uclVerifications;
    @Mock CollateralLoanVerificationRepository collateralVerifications;
    @Mock LoanReviewCycleRepository reviewCycles;
    @Mock LoanDocumentChecklistPort documents;

    @Test
    void decisionQueueUsesExactServerSideStatusProductAndPaging() {
        UUID applicationId = UUID.randomUUID();
        LoanApplication application = new LoanApplication(
                applicationId, UUID.randomUUID(), UUID.randomUUID(), "UCL-1",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED,
                LoanApplicationStatus.APPROVAL_PENDING, BigDecimal.TEN, 6,
                LocalDateTime.of(2026, 9, 6, 8, 0)
        );
        when(applications.findStaffPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.APPROVAL_PENDING,
                2,
                25
        )).thenReturn(new LoanApplicationRepository.StaffPage(2, 25, 51, 3, List.of(application)));
        ApprovalLoanCaseAdapter adapter = new ApprovalLoanCaseAdapter(
                applications, salaryAdvanceVerifications, uclVerifications,
                collateralVerifications, reviewCycles, documents
        );

        var result = adapter.findDecisionQueue("UNSECURED_CONSUMER_LOAN", 2, 25);

        assertEquals(applicationId, result.items().getFirst().loanApplicationId());
        assertEquals(51, result.totalElements());
        verify(applications).findStaffPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.APPROVAL_PENDING,
                2,
                25
        );
    }
}
