package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.ContractReadinessDto;
import com.meridian.platform.loan.application.dto.LoanContractBankAccountDto;
import com.meridian.platform.loan.application.dto.LoanContractDto;
import com.meridian.platform.loan.application.dto.LoanContractRepaymentItemDto;
import com.meridian.platform.loan.application.dto.StaffContractCaseDto;
import com.meridian.platform.loan.application.dto.StaffContractWorkPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffContractWorkUseCase;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffContractWorkControllerTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

    @Test
    void returnsPagedContractWorkAndForwardsFilters() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new StaffContractWorkController(new StubUseCase())
        ).build();

        mockMvc.perform(get("/api/v1/staff/contract-work")
                        .queryParam("productCode", "UNSECURED_CONSUMER_LOAN")
                        .queryParam("page", "1")
                        .queryParam("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.items[0].applicationStatus").value("CONTRACT_PENDING"))
                .andExpect(jsonPath("$.items[0].workStage").value("READY_TO_CONFIRM"));
    }

    @Test
    void caseExposesMaskedContractAndExcludesSensitiveOrInternalEvidence() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new StaffContractWorkController(new StubUseCase())
        ).build();

        mockMvc.perform(get(
                        "/api/v1/staff/loan-applications/{loanApplicationId}/contract",
                        APPLICATION_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentContract.disbursementBankAccount.maskedAccountNumber")
                        .value("****7890"))
                .andExpect(jsonPath("$.currentContract.repaymentPreview[0].totalDue").value(1100.00))
                .andExpect(jsonPath("$.readiness.calculationSemantics").value("POINT_IN_TIME_ADVISORY"))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.currentContract.fullAccountNumber").doesNotExist())
                .andExpect(jsonPath("$.currentContract.preparationRequestId").doesNotExist())
                .andExpect(jsonPath("$.currentContract.confirmationRequestId").doesNotExist())
                .andExpect(jsonPath("$.currentContract.preparedByUserId").doesNotExist())
                .andExpect(jsonPath("$.currentContract.disbursementBankAccount.ciphertext").doesNotExist())
                .andExpect(jsonPath("$.currentContract.disbursementBankAccount.nonce").doesNotExist())
                .andExpect(jsonPath("$.currentContract.disbursementBankAccount.keyId").doesNotExist());
    }

    private static class StubUseCase implements QueryStaffContractWorkUseCase {
        @Override
        public StaffContractWorkPageDto queryWork(ProductCode productCode, int page, int size) {
            return new StaffContractWorkPageDto(page, size, 26, 2, List.of(item()));
        }

        @Override
        public StaffContractCaseDto queryCase(UUID loanApplicationId) {
            return new StaffContractCaseDto(
                    loanApplicationId,
                    "UCL-20260907-000001",
                    "UNSECURED_CONSUMER_LOAN",
                    "UNSECURED",
                    new BigDecimal("1000.00"),
                    1,
                    "CONTRACT_PENDING",
                    LocalDateTime.of(2026, 9, 7, 8, 0),
                    contract(),
                    readiness(),
                    "READY_TO_CONFIRM"
            );
        }

        private static StaffContractWorkPageDto.ItemDto item() {
            return new StaffContractWorkPageDto.ItemDto(
                    APPLICATION_ID,
                    "UCL-20260907-000001",
                    "UNSECURED_CONSUMER_LOAN",
                    "UNSECURED",
                    new BigDecimal("1000.00"),
                    1,
                    "CONTRACT_PENDING",
                    LocalDateTime.of(2026, 9, 7, 8, 0),
                    contract(),
                    readiness(),
                    "READY_TO_CONFIRM"
            );
        }

        private static ContractReadinessDto readiness() {
            return new ContractReadinessDto(
                    APPLICATION_ID,
                    UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                    1,
                    true,
                    List.of(),
                    "POINT_IN_TIME_ADVISORY",
                    true
            );
        }

        private static LoanContractDto contract() {
            return new LoanContractDto(
                    UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                    "MCT-BBBBBBBB-BBBB-4BBB-8BBB-BBBBBBBBBBBB",
                    1,
                    "ACKNOWLEDGED",
                    new BigDecimal("1000.00"),
                    1,
                    "FLAT_ORIGINAL_PRINCIPAL",
                    new BigDecimal("0.100000"),
                    new BigDecimal("100.00"),
                    new BigDecimal("0.00"),
                    new BigDecimal("1100.00"),
                    "MONTHLY",
                    List.of(new LoanContractRepaymentItemDto(
                            1,
                            new BigDecimal("1000.00"),
                            new BigDecimal("100.00"),
                            new BigDecimal("0.00"),
                            new BigDecimal("1100.00")
                    )),
                    new LoanContractBankAccountDto(
                            "VCB",
                            "Vietcombank",
                            "MERIDIAN CUSTOMER",
                            "****7890",
                            true,
                            true,
                            LocalDateTime.of(2026, 9, 7, 8, 0)
                    ),
                    LocalDateTime.of(2026, 9, 7, 8, 0),
                    LocalDateTime.of(2026, 9, 7, 8, 30),
                    null,
                    null
            );
        }
    }
}
