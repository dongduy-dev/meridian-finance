package com.meridian.platform.loan.application.mapper;

import tools.jackson.databind.json.JsonMapper;
import com.meridian.platform.loan.application.dto.ContractReadinessDto;
import com.meridian.platform.loan.application.dto.LoanContractDto;
import com.meridian.platform.loan.application.port.in.QueryContractReadinessUseCase;
import com.meridian.platform.loan.domain.model.ContractReadinessBlockerCode;
import com.meridian.platform.loan.testsupport.LoanContractTestData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanContractMapperTest {

    private final LoanContractMapper mapper = new LoanContractMapper();

    @Test
    void mapsOnlyApprovedSafeContractFields() throws Exception {
        LoanContractDto dto = mapper.toDto(LoanContractTestData.prepared());
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(dto);

        assertEquals("****7890", dto.disbursementBankAccount().maskedAccountNumber());
        assertEquals("ACKNOWLEDGE", dto.availableCustomerAction());
        assertTrue(json.contains("MERIDIAN CUSTOMER"));
        assertFalse(json.contains("ciphertext"));
        assertFalse(json.contains("nonce"));
        assertFalse(json.contains("keyId"));
        assertFalse(json.contains("aadVersion"));
        assertFalse(json.contains("sourceBankAccountId"));
        assertFalse(json.contains("customerId"));
        assertFalse(json.contains("preparationRequestId"));
    }

    @Test
    void mapsStableAdvisoryReadinessSemanticsAndBlockerCodes() {
        ContractReadinessDto dto = mapper.toDto(new QueryContractReadinessUseCase.Snapshot(
                LoanContractTestData.APPLICATION_ID,
                LoanContractTestData.CONTRACT_ID,
                1,
                false,
                List.of(
                        ContractReadinessBlockerCode.DOCUMENTS_NOT_PROCESSING_READY,
                        ContractReadinessBlockerCode.CAPTURED_ACCOUNT_INACTIVE
                )
        ));

        assertEquals(
                List.of("DOCUMENTS_NOT_PROCESSING_READY", "CAPTURED_ACCOUNT_INACTIVE"),
                dto.blockerCodes()
        );
        assertEquals("POINT_IN_TIME_ADVISORY", dto.calculationSemantics());
        assertTrue(dto.recomputedDuringConfirmation());
    }

    @Test
    void removesCustomerActionAfterAcknowledgment() {
        assertNull(mapper.toDto(LoanContractTestData.acknowledged()).availableCustomerAction());
    }
}
