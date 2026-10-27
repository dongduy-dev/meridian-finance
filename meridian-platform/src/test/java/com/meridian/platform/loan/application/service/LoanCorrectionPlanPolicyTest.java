package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanCorrectionPlanRequest;
import com.meridian.platform.loan.application.dto.LoanCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.correction.CorrectionScope;
import com.meridian.platform.loan.application.dto.correction.CorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanDocumentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class LoanCorrectionPlanPolicyTest {
    @Test
    void requestMappingPreservesNullsAndTextUntilStructuralValidation() {
        var tasks = new ArrayList<LoanCorrectionTaskRequest>();
        tasks.add(null);
        var task = new LoanCorrectionTaskRequest(CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                CorrectionResponsibility.STAFF, LoanDocumentType.RECENT_PAYSLIP, true, null, null, null, "  Upload\n");
        tasks.add(task);
        var plan = new LoanCorrectionPlanRequest(tasks).toPlan();
        tasks.clear();
        assertNull(plan.tasks().getFirst());
        assertEquals("  Upload\n", plan.tasks().get(1).staffInstruction());
        assertThrows(UnsupportedOperationException.class, () -> plan.tasks().clear());
        assertNull(new LoanCorrectionPlanRequest(null).toPlan().tasks());
    }

    @Test
    void loanStructureAllowsStaffTasksWithoutApprovalActionRules() {
        var task = new LoanCorrectionTaskRequest(CorrectionScope.SUPPORTING_DOCUMENT_UPLOAD,
                CorrectionResponsibility.STAFF, LoanDocumentType.RECENT_PAYSLIP, true, null, null, null, "Upload");
        assertDoesNotThrow(() -> new LoanCorrectionPlanPolicy().validate(new LoanCorrectionPlanRequest(java.util.List.of(task)).toPlan()));
    }
}
