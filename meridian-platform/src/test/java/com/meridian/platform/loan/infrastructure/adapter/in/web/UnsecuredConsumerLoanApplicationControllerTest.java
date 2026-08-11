package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationRequest;
import com.meridian.platform.loan.application.port.in.StartUnsecuredConsumerLoanApplicationUseCase;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnsecuredConsumerLoanApplicationControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UnsecuredConsumerLoanApplicationController(new StubUseCase()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createsUclFromMinimalRequest() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/unsecured-consumer-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedAmount": 5000000,
                                  "requestedTermMonths": 6
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productCode").value("UNSECURED_CONSUMER_LOAN"))
                .andExpect(jsonPath("$.status").value("DOCUMENTS_PENDING"))
                .andExpect(jsonPath("$.productVerificationResult").value("PENDING_MANUAL_REVIEW"));
    }

    @Test
    void rejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/unsecured-consumer-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private static class StubUseCase implements StartUnsecuredConsumerLoanApplicationUseCase {
        @Override
        public UnsecuredConsumerLoanApplicationDto startUnsecuredConsumerLoanApplication(
                UnsecuredConsumerLoanApplicationRequest request
        ) {
            return new UnsecuredConsumerLoanApplicationDto(
                    UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                    "UCL-20260811-000001",
                    "UNSECURED_CONSUMER_LOAN",
                    "UNSECURED",
                    "DOCUMENTS_PENDING",
                    new BigDecimal("5000000"),
                    6,
                    "PENDING_MANUAL_REVIEW",
                    LocalDateTime.parse("2026-08-11T09:00:00")
            );
        }
    }
}
