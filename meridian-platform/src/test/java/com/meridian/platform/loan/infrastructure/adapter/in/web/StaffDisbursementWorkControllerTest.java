package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.LoanContractBankAccountDto;
import com.meridian.platform.loan.application.dto.LoanContractDto;
import com.meridian.platform.loan.application.dto.LoanContractRepaymentItemDto;
import com.meridian.platform.loan.application.dto.StaffDisbursementActivationDto;
import com.meridian.platform.loan.application.dto.StaffDisbursementCaseDto;
import com.meridian.platform.loan.application.dto.StaffDisbursementContractDto;
import com.meridian.platform.loan.application.dto.StaffDisbursementWorkPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffDisbursementWorkUseCase;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffDisbursementWorkControllerTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID CONTRACT_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID ACCOUNT_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID SCHEDULE_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

    @Test
    void returnsPagedReadyDisbursementWork() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new StaffDisbursementWorkController(new StubUseCase())
        ).build();

        mockMvc.perform(get("/api/v1/staff/disbursement-work")
                        .queryParam("productCode", "UNSECURED_CONSUMER_LOAN")
                        .queryParam("page", "1")
                        .queryParam("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.items[0].applicationStatus")
                        .value("DISBURSEMENT_PENDING"))
                .andExpect(jsonPath("$.items[0].workStage").value("READY_TO_DISBURSE"))
                .andExpect(jsonPath("$.items[0].currentContract.disbursementDestination.maskedAccountNumber")
                        .value("****7890"));
    }

    @Test
    void completedCaseReturnsSafeActivationAndExcludesSensitiveEvidence() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new StaffDisbursementWorkController(new StubUseCase())
        ).build();

        mockMvc.perform(get(
                        "/api/v1/staff/loan-applications/{loanApplicationId}/disbursement",
                        APPLICATION_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("DISBURSED"))
                .andExpect(jsonPath("$.workStage").value("DISBURSED"))
                .andExpect(jsonPath("$.activation.loanAccountId").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.activation.scheduleType").value("FINAL"))
                .andExpect(jsonPath("$.activation.scheduleItems[0].dueDate").value("2026-10-10"))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.activation.externalTransferReference").doesNotExist())
                .andExpect(jsonPath("$.activation.requestId").doesNotExist())
                .andExpect(jsonPath("$.activation.confirmedByUserId").doesNotExist())
                .andExpect(jsonPath("$.currentContract.disbursementBankAccount.accountNumber")
                        .doesNotExist())
                .andExpect(jsonPath("$.currentContract.disbursementBankAccount.ciphertext")
                        .doesNotExist())
                .andExpect(jsonPath("$.currentContract.disbursementBankAccount.nonce")
                        .doesNotExist())
                .andExpect(jsonPath("$.currentContract.disbursementBankAccount.keyId")
                        .doesNotExist())
                .andExpect(jsonPath("$.currentContract.disbursementBankAccount.aad")
                        .doesNotExist());
    }

    private static class StubUseCase implements QueryStaffDisbursementWorkUseCase {
        @Override
        public StaffDisbursementWorkPageDto queryWork(ProductCode productCode, int page, int size) {
            return new StaffDisbursementWorkPageDto(page, size, 26, 2, List.of(new StaffDisbursementWorkPageDto.ItemDto(
                    APPLICATION_ID,
                    "UCL-20260910-000001",
                    "UNSECURED_CONSUMER_LOAN",
                    "UNSECURED",
                    money(1_000),
                    1,
                    "DISBURSEMENT_PENDING",
                    LocalDateTime.of(2026, 9, 10, 8, 0),
                    contractSummary(),
                    "READY_TO_DISBURSE"
            )));
        }

        @Override
        public StaffDisbursementCaseDto queryCase(UUID loanApplicationId) {
            return new StaffDisbursementCaseDto(
                    loanApplicationId,
                    "UCL-20260910-000001",
                    "UNSECURED_CONSUMER_LOAN",
                    "UNSECURED",
                    money(1_000),
                    1,
                    "DISBURSED",
                    LocalDateTime.of(2026, 9, 10, 8, 0),
                    contract(),
                    activation(),
                    "DISBURSED"
            );
        }

        private static StaffDisbursementContractDto contractSummary() {
            return new StaffDisbursementContractDto(
                    CONTRACT_ID,
                    "MCT-BBBBBBBB-BBBB-4BBB-8BBB-BBBBBBBBBBBB",
                    1,
                    "READY_FOR_DISBURSEMENT",
                    money(1_000),
                    1,
                    "MONTHLY",
                    LocalDateTime.of(2026, 9, 10, 9, 0),
                    new StaffDisbursementContractDto.DestinationDto(
                            "VCB", "Vietcombank", "MERIDIAN CUSTOMER", "****7890"
                    )
            );
        }

        private static LoanContractDto contract() {
            return new LoanContractDto(
                    CONTRACT_ID,
                    "MCT-BBBBBBBB-BBBB-4BBB-8BBB-BBBBBBBBBBBB",
                    1,
                    "READY_FOR_DISBURSEMENT",
                    money(1_000),
                    1,
                    "FLAT_ORIGINAL_PRINCIPAL",
                    new BigDecimal("0.100000"),
                    money(100),
                    money(0),
                    money(1_100),
                    "MONTHLY",
                    List.of(new LoanContractRepaymentItemDto(
                            1, money(1_000), money(100), money(0), money(1_100)
                    )),
                    new LoanContractBankAccountDto(
                            "VCB", "Vietcombank", "MERIDIAN CUSTOMER", "****7890", true, true,
                            LocalDateTime.of(2026, 9, 10, 8, 0)
                    ),
                    LocalDateTime.of(2026, 9, 10, 8, 0),
                    LocalDateTime.of(2026, 9, 10, 8, 30),
                    LocalDateTime.of(2026, 9, 10, 9, 0),
                    null
            );
        }

        private static StaffDisbursementActivationDto activation() {
            return new StaffDisbursementActivationDto(
                    ACCOUNT_ID,
                    "LA-CCCCCCCCCCCC4CCC8CCCCCCCCCCCCCCC",
                    "ACTIVE",
                    LocalDateTime.of(2026, 9, 10, 10, 0),
                    money(1_000),
                    LocalDate.of(2026, 9, 10),
                    LocalDate.of(2026, 10, 10),
                    SCHEDULE_ID,
                    "FINAL",
                    1,
                    List.of(new StaffDisbursementActivationDto.ScheduleItemDto(
                            1,
                            LocalDate.of(2026, 10, 10),
                            money(1_000),
                            money(100),
                            money(0),
                            money(1_100)
                    ))
            );
        }

        private static BigDecimal money(long amount) {
            return BigDecimal.valueOf(amount).setScale(2);
        }
    }
}
