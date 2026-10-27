package com.meridian.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.aop.support.AopUtils;

import com.meridian.platform.document.application.port.in.DocumentChecklistUseCase;
import com.meridian.platform.document.application.port.in.LoanIntakeEvidenceUseCase;
import com.meridian.platform.document.application.port.in.AssistedActionEvidenceUseCase;
import com.meridian.platform.document.application.service.DocumentChecklistService;
import com.meridian.platform.document.application.service.LoanIntakeEvidenceService;
import com.meridian.platform.document.application.service.AssistedActionEvidenceService;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanIntakeEvidencePort;
import com.meridian.platform.loan.application.port.out.LoanAssistedActionEvidencePort;
import com.meridian.platform.loan.infrastructure.adapter.out.document.LoanDocumentChecklistAdapter;
import com.meridian.platform.loan.infrastructure.adapter.out.document.LoanIntakeEvidenceAdapter;
import com.meridian.platform.loan.infrastructure.adapter.out.document.LoanAssistedActionEvidenceAdapter;

import com.meridian.platform.approval.application.spi.loan.ApprovalLoanCaseSpi;
import com.meridian.platform.loan.application.port.in.QueryApprovalLoanCaseUseCase;
import com.meridian.platform.loan.application.service.QueryApprovalLoanCaseService;
import com.meridian.platform.loan.infrastructure.adapter.in.approval.ApprovalLoanCaseAdapter;
import com.meridian.platform.approval.application.spi.loan.ApprovalLoanReviewCycleSpi;
import com.meridian.platform.loan.application.port.in.QueryApprovalLoanReviewCycleUseCase;
import com.meridian.platform.loan.application.service.QueryApprovalLoanReviewCycleService;
import com.meridian.platform.loan.infrastructure.adapter.in.approval.ApprovalLoanReviewCycleAdapter;
import com.meridian.platform.approval.application.spi.loan.ApprovalLoanReviewHistorySpi;
import com.meridian.platform.loan.application.port.in.QueryApprovalLoanReviewHistoryUseCase;
import com.meridian.platform.loan.application.service.QueryApprovalLoanReviewHistoryService;
import com.meridian.platform.loan.infrastructure.adapter.in.approval.ApprovalLoanReviewHistoryAdapter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "meridian.loan.offer-expiry.enabled=false")
class MeridianPlatformApplicationTests {

    private static final String TEST_SCHEMA = "meridian_test_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired private ApplicationContext context;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
    }

    @Test
    void contextLoads() {
    }

    @Test
    void loanDocumentPortsAndDocumentPublicInputsHaveOneProductionBinding() {
        assertSingleBinding(LoanDocumentChecklistPort.class, LoanDocumentChecklistAdapter.class);
        assertSingleBinding(LoanIntakeEvidencePort.class, LoanIntakeEvidenceAdapter.class);
        assertSingleBinding(LoanAssistedActionEvidencePort.class, LoanAssistedActionEvidenceAdapter.class);
        assertSingleBinding(DocumentChecklistUseCase.class, DocumentChecklistService.class);
        assertSingleBinding(LoanIntakeEvidenceUseCase.class, LoanIntakeEvidenceService.class);
        assertSingleBinding(AssistedActionEvidenceUseCase.class, AssistedActionEvidenceService.class);
    }

    @Test
    void approvalSpisAndLoanQueriesHaveExactlyOneProductionBindingAndNoRetiredPorts() {
        assertSingleBinding(ApprovalLoanCaseSpi.class, ApprovalLoanCaseAdapter.class);
        assertSingleBinding(QueryApprovalLoanCaseUseCase.class, QueryApprovalLoanCaseService.class);
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "com.meridian.platform.approval.application.port.out.ApprovalLoanCasePort"));
        assertSingleBinding(ApprovalLoanReviewCycleSpi.class, ApprovalLoanReviewCycleAdapter.class);
        assertSingleBinding(QueryApprovalLoanReviewCycleUseCase.class, QueryApprovalLoanReviewCycleService.class);
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "com.meridian.platform.approval.application.port.out.ApprovalLoanReviewCyclePort"));
        assertSingleBinding(ApprovalLoanReviewHistorySpi.class, ApprovalLoanReviewHistoryAdapter.class);
        assertSingleBinding(QueryApprovalLoanReviewHistoryUseCase.class, QueryApprovalLoanReviewHistoryService.class);
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "com.meridian.platform.approval.application.port.out.ApprovalLoanReviewHistoryPort"));
    }

    private void assertSingleBinding(Class<?> contract, Class<?> implementation) {
        var beans = context.getBeansOfType(contract);
        assertEquals(1, beans.size(), contract.getName());
        assertEquals(implementation, AopUtils.getTargetClass(beans.values().iterator().next()));
    }

}
