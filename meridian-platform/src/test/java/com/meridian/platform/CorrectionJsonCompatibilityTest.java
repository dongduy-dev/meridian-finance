package com.meridian.platform;

import com.meridian.platform.approval.application.dto.*;
import com.meridian.platform.document.application.dto.*;
import com.meridian.platform.document.application.port.in.ReviewDocumentUseCase;
import com.meridian.platform.document.application.service.ReviewDocumentService;
import com.meridian.platform.document.infrastructure.adapter.in.web.DocumentReviewController;
import com.meridian.platform.loan.application.dto.*;
import com.meridian.platform.loan.application.service.LoanCorrectionPlanPolicy;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CorrectionJsonCompatibilityTest {
    private static final String ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String PLAN = """
            {"tasks":[{"scope":"DOCUMENT_REPLACEMENT","responsibleParty":"CUSTOMER",
            "documentType":"BANK_STATEMENT","createChecklistItem":false,
            "checklistItemId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "baselineDocumentVersionId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            "customerInstruction":"  Replace exactly  ","staffInstruction":null}]}
            """;
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void baselineApprovalAndLoanJsonRetainFieldsNestingEnumsAndInstructions() {
        var approval = json.readValue(PLAN, CorrectionPlanRequest.class);
        var loan = json.readValue(PLAN, LoanCorrectionPlanRequest.class);
        assertEquals(json.readTree(PLAN), json.valueToTree(approval));
        assertEquals(json.valueToTree(approval), json.valueToTree(loan));
        assertEquals("  Replace exactly  ", loan.toPlan().tasks().getFirst().customerInstruction());
        String recommendation = "{\"action\":\"RETURN_TO_CUSTOMER_REVISION\",\"expectedReviewCycleId\":\"" + ID
                + "\",\"reasonCode\":\"DOCUMENT_REPLACEMENT_REQUIRED\",\"correctionPlan\":" + PLAN + "}";
        assertEquals("DOCUMENT_REPLACEMENT_REQUIRED", json.readValue(recommendation, ReviewRecommendationRequest.class).reasonCode().name());
        String decision = "{\"action\":\"REQUEST_CUSTOMER_OR_STAFF_CORRECTION\",\"expectedReviewCycleId\":\"" + ID
                + "\",\"expectedReviewRecommendationId\":\"" + ID
                + "\",\"reasonCode\":\"DOCUMENT_REPLACEMENT_REQUIRED\",\"correctionPlan\":" + PLAN + "}";
        assertEquals(approval, json.readValue(decision, ApprovalDecisionRequest.class).correctionPlan());
        String ucl = "{\"outcome\":\"REQUIRES_MORE_INFORMATION\",\"assessmentNote\":\"Check evidence\",\"reasonCode\":\"DOCUMENT_REPLACEMENT_REQUIRED\",\"correctionPlan\":" + PLAN + "}";
        assertEquals(loan, json.readValue(ucl, CompleteUnsecuredConsumerLoanVerificationRequest.class).correctionPlan());
        String collateral = "{\"expectedVerificationId\":\"" + ID + "\"," + ucl.substring(1);
        assertEquals(loan, json.readValue(collateral, CompleteCollateralLoanVerificationRequest.class).correctionPlan());
        assertThrows(RuntimeException.class, () -> json.readValue(ucl.replace("\"outcome\"", "\"unexpected\":true,\"outcome\""), CompleteUnsecuredConsumerLoanVerificationRequest.class));
        assertThrows(RuntimeException.class, () -> json.readValue(collateral.replace("\"outcome\"", "\"unexpected\":true,\"outcome\""), CompleteCollateralLoanVerificationRequest.class));
    }

    @Test
    void requestScopeStillParsesTermsBeforeReturningTheStableBusinessError() {
        String terms = PLAN.replace("DOCUMENT_REPLACEMENT", "APPLICATION_TERMS");
        var request = json.readValue(terms, LoanCorrectionPlanRequest.class);
        var error = assertThrows(com.meridian.platform.shared.domain.exception.BusinessRuleViolationException.class,
                () -> new LoanCorrectionPlanPolicy().validate(request.toPlan()));
        assertEquals("CORRECTION_FIELD_NOT_ALLOWED", error.getErrorCode());
        assertThrows(RuntimeException.class, () -> json.readValue(PLAN.replace("DOCUMENT_REPLACEMENT", "UNKNOWN_SCOPE"), LoanCorrectionPlanRequest.class));
        assertThrows(RuntimeException.class, () -> json.readValue(PLAN.replace("CUSTOMER", "UNKNOWN_PARTY"), LoanCorrectionPlanRequest.class));
    }

    @Test
    void nestedBeanValidationStillRejectsTheSameInstructionAndPlanSizes() {
        try (var validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            String tooLong = PLAN.replace("  Replace exactly  ", "x".repeat(501));
            var approvalErrors = validator.validate(json.readValue(tooLong, CorrectionPlanRequest.class));
            var loanErrors = validator.validate(json.readValue(tooLong, LoanCorrectionPlanRequest.class));
            assertEquals(1, approvalErrors.size());
            assertEquals(approvalErrors.stream().map(error -> error.getPropertyPath() + ":" + error.getMessage()).toList(),
                    loanErrors.stream().map(error -> error.getPropertyPath() + ":" + error.getMessage()).toList());
            assertEquals(1, validator.validate(json.readValue("{\"tasks\":[]}", LoanCorrectionPlanRequest.class)).size());
            assertTrue(validator.validate(json.readValue("{\"tasks\":null}", LoanCorrectionPlanRequest.class)).isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(DocumentCorrectionReasonCode.class)
    void documentReviewRecognizesEveryBaselineReasonAndRejectsWrongReasonsAsBusinessErrors(DocumentCorrectionReasonCode reason) throws Exception {
        UUID id = UUID.fromString(ID);
        var actor = new AuthenticatedUser(id, "reviewer@meridian.test", "STAFF", null, Set.of("LOAN_OFFICER"), Set.of("document:review"));
        ReviewDocumentUseCase validation = command -> new ReviewDocumentService(null, null, null, null, null, null, null).review(command);
        var port = mock(ReviewDocumentUseCase.class);
        when(port.review(any())).thenAnswer(call -> {
            ReviewDocumentCommand command = call.getArgument(0);
            assertEquals(reason, command.correctionReasonCode());
            if (reason != DocumentCorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED) return validation.review(command);
            return new DocumentReviewDto(id, id, id, "REQUEST_REPLACEMENT", null, LocalDateTime.of(2026, 10, 27, 12, 0));
        });
        try (var validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            var mvc = MockMvcBuilders.standaloneSetup(new DocumentReviewController(port, () -> actor))
                    .setValidator(validator).setControllerAdvice(new GlobalExceptionHandler()).build();
            String body = "{\"reviewRequestId\":\"" + ID + "\",\"documentVersionId\":\"" + ID
                    + "\",\"outcome\":\"REQUEST_REPLACEMENT\",\"correctionReasonCode\":\"" + reason
                    + "\",\"customerInstruction\":\"Replace\"}";
            var result = mvc.perform(post("/api/v1/loan-applications/{app}/document-review-items/{item}/reviews", id, id)
                    .contentType(MediaType.APPLICATION_JSON).content(body));
            if (reason == DocumentCorrectionReasonCode.DOCUMENT_REPLACEMENT_REQUIRED) result.andExpect(status().isOk());
            else result.andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CORRECTION_PLAN"))
                    .andExpect(jsonPath("$.message").value("Replacement review requires a controlled reason and customer instruction."));
            mvc.perform(post("/api/v1/loan-applications/{app}/document-review-items/{item}/reviews", id, id)
                    .contentType(MediaType.APPLICATION_JSON).content(body.replace(reason.name(), "UNKNOWN_REASON")))
                    .andExpect(status().isBadRequest());
        }
    }
}
