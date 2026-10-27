package com.meridian.platform;

import com.meridian.platform.approval.application.service.CorrectionPlanPolicy;
import com.meridian.platform.loan.application.dto.LoanCorrectionPlanRequest;
import com.meridian.platform.loan.application.dto.LoanCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.correction.CorrectionScope;
import com.meridian.platform.loan.application.dto.correction.CorrectionResponsibility;
import com.meridian.platform.loan.application.service.LoanCorrectionPlanPolicy;
import com.meridian.platform.loan.domain.model.LoanDocumentType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.testsupport.CorrectionPolicyCompatibilityCases.Case;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class CorrectionPolicyCompatibilityTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.meridian.platform.testsupport.CorrectionPolicyCompatibilityCases#cases")
    void independentValidatorsRetainBaselineErrorsAndPrecedence(Case vector) {
        var source = vector.plan();
        var loan = source == null ? null : new LoanCorrectionPlanRequest(source.tasks() == null ? null : source.tasks().stream()
                .map(task -> task == null ? null : new LoanCorrectionTaskRequest(
                        task.scope() == null ? null : CorrectionScope.valueOf(task.scope().name()),
                        task.responsibleParty() == null ? null : CorrectionResponsibility.valueOf(task.responsibleParty().name()),
                        task.documentType() == null ? null : LoanDocumentType.valueOf(task.documentType().name()),
                        task.createChecklistItem(), task.checklistItemId(), task.baselineDocumentVersionId(),
                        task.customerInstruction(), task.staffInstruction())).toList());
        check(vector, () -> new CorrectionPlanPolicy().validate(source));
        check(vector, () -> new LoanCorrectionPlanPolicy().validate(loan == null ? null : loan.toPlan()));
    }

    private void check(Case vector, Runnable validation) {
        if (vector.code() == null) {
            assertDoesNotThrow(validation::run);
        } else {
            var error = assertThrows(BusinessRuleViolationException.class, validation::run);
            assertEquals(vector.code(), error.getErrorCode());
            assertEquals(vector.message(), error.getMessage());
        }
    }
}
