package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.LoanProductDto;
import com.meridian.platform.loan.application.port.in.QueryLoanProductUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoanProductControllerTest {

    @Test
    void exposesPublicPolicyPresentationWithoutInternalPolicyIdentity() throws Exception {
        LoanProductDto product = new LoanProductDto(
                "UNSECURED_CONSUMER_LOAN",
                "UNSECURED",
                "Unsecured Consumer Loan",
                "Flexible personal lending.",
                true,
                new BigDecimal("1000000.00"),
                new BigDecimal("100000000.00"),
                new LoanProductDto.PolicyPresentationDto(
                        List.of(3, 6, 9, 12),
                        new LoanProductDto.PricingPresentationDto(
                                new BigDecimal("0.018000"), BigDecimal.ZERO.setScale(2)),
                        "FLAT_ORIGINAL_PRINCIPAL",
                        "MONTHLY_INSTALLMENT",
                        7,
                        List.of(new LoanProductDto.SubmissionEvidenceRequirementDto(
                                "INCOME_PROOF", "REQUIRED")),
                        List.of("Customer readiness is required.")
                )
        );
        QueryLoanProductUseCase query = new QueryLoanProductUseCase() {
            @Override public List<LoanProductDto> findActiveLoanProducts() {
                return List.of(product);
            }

            @Override public LoanProductDto findByProductCode(String productCode) {
                return product;
            }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LoanProductController(query)).build();

        mvc.perform(get("/api/v1/loan-products/UNSECURED_CONSUMER_LOAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.allowedTermsMonths[3]").value(12))
                .andExpect(jsonPath("$.policy.pricing.flatMonthlyInterestRate").value(0.018))
                .andExpect(jsonPath("$.policy.submissionEvidenceRequirements[0].documentType")
                        .value("INCOME_PROOF"))
                .andExpect(jsonPath("$.policy.eligibilityNotes[0]")
                        .value("Customer readiness is required."))
                .andExpect(jsonPath("$.policyId").doesNotExist())
                .andExpect(jsonPath("$.policy.sourceId").doesNotExist());
    }
}
