package com.meridian.platform.loan.application.service;

import com.meridian.platform.document.domain.model.DocumentRequirementStatus;
import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.out.CollateralLoanOfferPolicyRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceOfferPolicyRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanOfferPolicyRepository;
import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.loan.domain.model.RepaymentMethod;
import com.meridian.platform.loan.domain.model.collateral.CollateralLoanOfferPolicy;
import com.meridian.platform.loan.domain.model.salaryadvance.SalaryAdvanceOfferPolicy;
import com.meridian.platform.loan.domain.model.unsecured.UnsecuredConsumerLoanOfferPolicy;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryLoanProductServiceTest {

    @Mock LoanProductRepository products;
    @Mock SalaryAdvanceOfferPolicyRepository salaryPolicies;
    @Mock UnsecuredConsumerLoanOfferPolicyRepository uclPolicies;
    @Mock CollateralLoanOfferPolicyRepository collateralPolicies;
    @Mock LoanDocumentChecklistPort documents;

    private QueryLoanProductService service;

    @BeforeEach
    void setUp() {
        service = new QueryLoanProductService(
                products,
                new LoanMapper(),
                salaryPolicies,
                uclPolicies,
                collateralPolicies,
                documents
        );
    }

    @Test
    void returnsActivePolicyTermsPricingAndDocumentOwnedEvidenceForEveryProduct() {
        LoanProduct salary = product(ProductCode.SALARY_ADVANCE, ProductType.SALARY_BASED);
        LoanProduct ucl = product(ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED);
        LoanProduct collateral = product(ProductCode.COLLATERAL_LOAN, ProductType.SECURED);
        when(products.findAllActive()).thenReturn(List.of(salary, ucl, collateral));
        when(salaryPolicies.findActiveDefaultPolicy()).thenReturn(Optional.of(new SalaryAdvanceOfferPolicy(
                UUID.randomUUID(), InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.012000"), BigDecimal.ZERO, RepaymentMethod.ON_SALARY_DATE,
                7, Set.of(1, 2, 3))));
        when(uclPolicies.findActiveDefaultPolicy()).thenReturn(Optional.of(new UnsecuredConsumerLoanOfferPolicy(
                UUID.randomUUID(), InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.018000"), BigDecimal.ZERO, RepaymentMethod.MONTHLY_INSTALLMENT,
                7, Set.of(3, 6, 9, 12))));
        when(collateralPolicies.findActiveDefaultPolicy()).thenReturn(Optional.of(new CollateralLoanOfferPolicy(
                UUID.randomUUID(), InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.015000"), BigDecimal.ZERO, RepaymentMethod.MONTHLY_INSTALLMENT,
                7, Set.of(6, 12, 18, 24))));
        when(documents.resolveSubmissionRequirements(ProductCode.SALARY_ADVANCE))
                .thenReturn(List.of());
        when(documents.resolveSubmissionRequirements(ProductCode.UNSECURED_CONSUMER_LOAN))
                .thenReturn(List.of(
                        requirement(DocumentType.INCOME_PROOF),
                        requirement(DocumentType.BANK_STATEMENT),
                        requirement(DocumentType.EMPLOYMENT_PROOF)
                ));
        when(documents.resolveSubmissionRequirements(ProductCode.COLLATERAL_LOAN))
                .thenReturn(List.of(requirement(DocumentType.COLLATERAL_OWNERSHIP_EVIDENCE)));

        var result = service.findActiveLoanProducts();

        assertEquals(List.of(1, 2, 3), result.get(0).policy().allowedTermsMonths());
        assertEquals(new BigDecimal("0.012000"),
                result.get(0).policy().pricing().flatMonthlyInterestRate());
        assertEquals(0, result.get(0).policy().submissionEvidenceRequirements().size());
        assertEquals(List.of("BANK_STATEMENT", "EMPLOYMENT_PROOF", "INCOME_PROOF"),
                result.get(1).policy().submissionEvidenceRequirements().stream()
                        .map(requirement -> requirement.documentType()).toList());
        assertEquals("COLLATERAL_OWNERSHIP_EVIDENCE",
                result.get(2).policy().submissionEvidenceRequirements().getFirst().documentType());
    }

    @Test
    void activeProductWithoutExecutablePolicyFailsClosed() {
        LoanProduct salary = product(ProductCode.SALARY_ADVANCE, ProductType.SALARY_BASED);
        when(products.findByProductCode(ProductCode.SALARY_ADVANCE)).thenReturn(Optional.of(salary));
        when(salaryPolicies.findActiveDefaultPolicy()).thenReturn(Optional.empty());

        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.findByProductCode("SALARY_ADVANCE")
        );

        assertEquals("PRODUCT_POLICY_INVALID", exception.getErrorCode());
    }

    private static LoanDocumentChecklistPort.SubmissionEvidenceRequirement requirement(
            DocumentType documentType
    ) {
        return new LoanDocumentChecklistPort.SubmissionEvidenceRequirement(
                documentType,
                DocumentRequirementStatus.REQUIRED
        );
    }

    private static LoanProduct product(ProductCode code, ProductType type) {
        return new LoanProduct(
                UUID.randomUUID(), code, type, code.name(), "Description", true,
                BigDecimal.valueOf(500_000), BigDecimal.valueOf(100_000_000)
        );
    }
}
