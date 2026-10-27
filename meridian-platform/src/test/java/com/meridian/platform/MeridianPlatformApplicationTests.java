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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

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

    private void assertSingleBinding(Class<?> contract, Class<?> implementation) {
        var beans = context.getBeansOfType(contract);
        assertEquals(1, beans.size(), contract.getName());
        assertEquals(implementation, AopUtils.getTargetClass(beans.values().iterator().next()));
    }

}
