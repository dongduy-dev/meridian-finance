package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.mapper.LoanContractMapper;
import com.meridian.platform.loan.application.port.in.AcknowledgeLoanContractUseCase;
import com.meridian.platform.loan.application.port.in.ConfirmContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.PrepareLoanContractUseCase;
import com.meridian.platform.loan.application.port.in.QueryContractReadinessUseCase;
import com.meridian.platform.loan.application.port.in.QueryCurrentLoanContractUseCase;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.testsupport.LoanContractTestData;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanContractControllerTest {

    private StubContractUseCases useCases;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCases = new StubContractUseCases();
        mockMvc = MockMvcBuilders.standaloneSetup(new LoanContractController(
                        useCases, useCases, useCases, useCases, useCases, new LoanContractMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exposesTheSafeOperationalWorkflowAndMapsCommands() throws Exception {
        UUID preparationRequestId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts", LoanContractTestData.APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preparationRequestId": "%s",
                                  "expectedCurrentContractVersion": 0,
                                  "supersessionReasonCode": null
                                }
                                """.formatted(preparationRequestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(LoanContractTestData.CONTRACT_ID.toString()))
                .andExpect(jsonPath("$.status").value("PREPARED"))
                .andExpect(jsonPath("$.disbursementBankAccount.maskedAccountNumber").value("****7890"))
                .andExpect(jsonPath("$.disbursementBankAccount.ciphertext").doesNotExist())
                .andExpect(jsonPath("$.disbursementBankAccount.keyId").doesNotExist());

        assertEquals(preparationRequestId, useCases.preparation.requestId());
        assertNull(useCases.preparation.supersessionReason());

        mockMvc.perform(get("/api/v1/loan-applications/{id}/contracts/current",
                        LoanContractTestData.APPLICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCustomerAction").value("ACKNOWLEDGE"));

        UUID acknowledgmentRequestId = UUID.randomUUID();
        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/contracts/current/acknowledgment",
                        LoanContractTestData.APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "acknowledgmentRequestId": "%s",
                                  "expectedContractVersion": 1
                                }
                                """.formatted(acknowledgmentRequestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        mockMvc.perform(get("/api/v1/loan-applications/{id}/contracts/current/readiness",
                        LoanContractTestData.APPLICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculationSemantics").value("POINT_IN_TIME_ADVISORY"))
                .andExpect(jsonPath("$.recomputedDuringConfirmation").value(true));
        assertNull(useCases.expectedReadinessVersion);

        mockMvc.perform(post(
                        "/api/v1/loan-applications/{id}/contracts/current/readiness/confirm",
                        LoanContractTestData.APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmationRequestId": "%s",
                                  "expectedContractVersion": 1
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_DISBURSEMENT"));
    }

    @Test
    void rejectsInvalidCommandShape() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts", LoanContractTestData.APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preparationRequestId": null,
                                  "expectedCurrentContractVersion": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void mapsStableApplicationConflictsWithoutInternalDetails() throws Exception {
        useCases.preparationFailure = new BusinessStateConflictException(
                "CONTRACT_VERSION_STALE",
                "Expected contract version is stale."
        );

        mockMvc.perform(post("/api/v1/loan-applications/{id}/contracts", LoanContractTestData.APPLICATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preparationRequestId": "%s",
                                  "expectedCurrentContractVersion": 1,
                                  "supersessionReasonCode": "DISBURSEMENT_ACCOUNT_REFRESH"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONTRACT_VERSION_STALE"));
    }

    private static final class StubContractUseCases implements
            PrepareLoanContractUseCase,
            QueryCurrentLoanContractUseCase,
            AcknowledgeLoanContractUseCase,
            QueryContractReadinessUseCase,
            ConfirmContractReadinessUseCase {

        private PrepareLoanContractUseCase.Command preparation;
        private Integer expectedReadinessVersion;
        private RuntimeException preparationFailure;

        @Override
        public LoanContract prepare(PrepareLoanContractUseCase.Command command) {
            preparation = command;
            if (preparationFailure != null) {
                throw preparationFailure;
            }
            return LoanContractTestData.prepared();
        }

        @Override
        public Optional<LoanContract> findCurrent(UUID loanApplicationId) {
            return Optional.of(LoanContractTestData.prepared());
        }

        @Override
        public LoanContract acknowledge(AcknowledgeLoanContractUseCase.Command command) {
            return LoanContractTestData.acknowledged();
        }

        @Override
        public QueryContractReadinessUseCase.Snapshot query(
                UUID loanApplicationId,
                Integer expectedContractVersion
        ) {
            expectedReadinessVersion = expectedContractVersion;
            return new QueryContractReadinessUseCase.Snapshot(
                    loanApplicationId,
                    LoanContractTestData.CONTRACT_ID,
                    1,
                    true,
                    List.of()
            );
        }

        @Override
        public LoanContract confirm(ConfirmContractReadinessUseCase.Command command) {
            return LoanContractTestData.ready();
        }
    }
}
