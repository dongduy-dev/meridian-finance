package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CollateralAssessmentSnapshotDto;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationVerificationDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationVerificationUseCase;
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

class StaffLoanApplicationVerificationControllerTest {

    private static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID VERIFICATION_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    @Test
    void serializesPurposeLimitedManualVerificationEvidenceWithoutRestrictedFields() throws Exception {
        QueryStaffLoanApplicationVerificationUseCase useCase = ignored -> new StaffLoanApplicationVerificationDto(
                APPLICATION_ID,
                "CL-20260905-000001",
                "COLLATERAL_LOAN",
                "SECURED",
                new BigDecimal("10000000"),
                12,
                "VERIFICATION_PENDING",
                LocalDateTime.of(2026, 9, 5, 8, 0),
                new StaffLoanApplicationVerificationDto.DocumentReadinessDto(true, true),
                new StaffLoanApplicationVerificationDto.ActionPresentationDto(false, true),
                new StaffLoanApplicationVerificationDto.ManualVerificationDto(
                        cycle(),
                        List.of(cycle()),
                        new CollateralAssessmentSnapshotDto(
                                "CAR",
                                "Customer vehicle",
                                new BigDecimal("25000000"),
                                "Customer-owned",
                                "Operational condition"
                        )
                ),
                List.of(new StaffLoanApplicationVerificationDto.CorrectionTargetDto(
                        UUID.randomUUID(),
                        "COLLATERAL_OWNERSHIP_EVIDENCE",
                        "REQUIRED",
                        UUID.randomUUID()
                ))
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new StaffLoanApplicationVerificationController(useCase)
        ).build();

        mockMvc.perform(get(
                        "/api/v1/staff/loan-applications/{loanApplicationId}/verification",
                        APPLICATION_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productVerification.currentCycle.verificationId")
                        .value(VERIFICATION_ID.toString()))
                .andExpect(jsonPath("$.productVerification.history[0].verificationSequence").value(1))
                .andExpect(jsonPath("$.productVerification.collateral.collateralType").value("CAR"))
                .andExpect(jsonPath("$.actions.completeAvailable").value(true))
                .andExpect(jsonPath("$.correctionTargets[0].documentType")
                        .value("COLLATERAL_OWNERSHIP_EVIDENCE"))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.reviewedByUserId").doesNotExist())
                .andExpect(jsonPath("$.assessmentNote").doesNotExist())
                .andExpect(jsonPath("$.sourceCorrectionRequestId").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.originalFilename").doesNotExist());
    }

    private static StaffLoanApplicationVerificationDto.VerificationCycleDto cycle() {
        return new StaffLoanApplicationVerificationDto.VerificationCycleDto(
                VERIFICATION_ID,
                1,
                "PENDING_MANUAL_REVIEW",
                LocalDateTime.of(2026, 9, 5, 8, 0),
                null
        );
    }
}
