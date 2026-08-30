package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanProductDto;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.in.QueryLoanProductUseCase;
import com.meridian.platform.loan.application.port.out.CollateralLoanOfferPolicyRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.port.out.SalaryAdvanceOfferPolicyRepository;
import com.meridian.platform.loan.application.port.out.UnsecuredConsumerLoanOfferPolicyRepository;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class QueryLoanProductService implements QueryLoanProductUseCase {

    private final LoanProductRepository loanProductRepository;
    private final LoanMapper loanMapper;
    private final SalaryAdvanceOfferPolicyRepository salaryAdvancePolicies;
    private final UnsecuredConsumerLoanOfferPolicyRepository unsecuredConsumerLoanPolicies;
    private final CollateralLoanOfferPolicyRepository collateralLoanPolicies;
    private final LoanDocumentChecklistPort documentChecklists;

    public QueryLoanProductService(
            LoanProductRepository loanProductRepository,
            LoanMapper loanMapper,
            SalaryAdvanceOfferPolicyRepository salaryAdvancePolicies,
            UnsecuredConsumerLoanOfferPolicyRepository unsecuredConsumerLoanPolicies,
            CollateralLoanOfferPolicyRepository collateralLoanPolicies,
            LoanDocumentChecklistPort documentChecklists
    ) {
        this.loanProductRepository = loanProductRepository;
        this.loanMapper = loanMapper;
        this.salaryAdvancePolicies = salaryAdvancePolicies;
        this.unsecuredConsumerLoanPolicies = unsecuredConsumerLoanPolicies;
        this.collateralLoanPolicies = collateralLoanPolicies;
        this.documentChecklists = documentChecklists;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanProductDto> findActiveLoanProducts() {
        return loanProductRepository.findAllActive()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LoanProductDto findByProductCode(String productCode) {
        ProductCode parsedProductCode = parsedProductCode(productCode);

        return loanProductRepository.findByProductCode(parsedProductCode)
                .filter(LoanProduct::active)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PRODUCT_NOT_FOUND",
                        "Product was not found."
                ));
    }

    private LoanProductDto toDto(LoanProduct product) {
        LoanProductDto.PolicyPresentationDto policy = switch (product.productCode()) {
            case SALARY_ADVANCE -> {
                var source = salaryAdvancePolicies.findActiveDefaultPolicy()
                        .orElseThrow(() -> policyInvalid("Salary Advance"));
                yield presentation(
                        source.allowedTermsMonths().stream().sorted().toList(),
                        source.flatMonthlyInterestRate(),
                        source.feeAmount(),
                        source.interestCalculationMethod().name(),
                        source.repaymentMethod().name(),
                        source.offerValidityDays(),
                        List.of(
                                "A complete Customer profile and eligible primary bank account are required.",
                                "Verified current Partner employment and available Salary Advance limit are required.",
                                "A blocking application or positive outstanding Salary Advance debt prevents submission."
                        ),
                        product.productCode()
                );
            }
            case UNSECURED_CONSUMER_LOAN -> {
                var source = unsecuredConsumerLoanPolicies.findActiveDefaultPolicy()
                        .orElseThrow(() -> policyInvalid("Unsecured Consumer Loan"));
                yield presentation(
                        source.allowedTermsMonths().stream().sorted().toList(),
                        source.flatMonthlyInterestRate(),
                        source.feeAmount(),
                        source.interestCalculationMethod().name(),
                        source.repaymentMethod().name(),
                        source.offerValidityDays(),
                        List.of(
                                "A complete Customer profile and eligible primary bank account are required.",
                                "Income and employment evidence is assessed through manual verification.",
                                "A blocking application or positive outstanding Unsecured Consumer Loan debt prevents submission."
                        ),
                        product.productCode()
                );
            }
            case COLLATERAL_LOAN -> {
                var source = collateralLoanPolicies.findActiveDefaultPolicy()
                        .orElseThrow(() -> policyInvalid("Collateral Loan"));
                yield presentation(
                        source.allowedTermsMonths().stream().sorted().toList(),
                        source.flatMonthlyInterestRate(),
                        source.feeAmount(),
                        source.interestCalculationMethod().name(),
                        source.repaymentMethod().name(),
                        source.offerValidityDays(),
                        List.of(
                                "A complete Customer profile and eligible primary bank account are required.",
                                "One submitted collateral asset and ownership evidence are assessed manually.",
                                "Estimated collateral value does not produce an automated loan-to-value decision."
                        ),
                        product.productCode()
                );
            }
        };
        return loanMapper.toLoanProductDto(product, policy);
    }

    private LoanProductDto.PolicyPresentationDto presentation(
            List<Integer> allowedTerms,
            java.math.BigDecimal monthlyRate,
            java.math.BigDecimal feeAmount,
            String interestMethod,
            String repaymentMethod,
            int offerValidityDays,
            List<String> eligibilityNotes,
            ProductCode productCode
    ) {
        List<LoanProductDto.SubmissionEvidenceRequirementDto> evidence = documentChecklists
                .resolveSubmissionRequirements(productCode)
                .stream()
                .sorted(Comparator.comparing(requirement -> requirement.documentType().name()))
                .map(requirement -> new LoanProductDto.SubmissionEvidenceRequirementDto(
                        requirement.documentType().name(),
                        requirement.requirementStatus().name()
                ))
                .toList();
        return new LoanProductDto.PolicyPresentationDto(
                allowedTerms,
                new LoanProductDto.PricingPresentationDto(monthlyRate, feeAmount),
                interestMethod,
                repaymentMethod,
                offerValidityDays,
                evidence,
                eligibilityNotes
        );
    }

    private static BusinessRuleViolationException policyInvalid(String productName) {
        return new BusinessRuleViolationException(
                "PRODUCT_POLICY_INVALID",
                productName + " active product policy was not found."
        );
    }

    private ProductCode parsedProductCode(String productCode) {
        try{
            return ProductCode.valueOf(productCode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e){
            throw new EntityNotFoundException(
                    "PRODUCT_CODE_NOT_FOUND",
                    "Product code was not found."
            );
        }
    }
}
