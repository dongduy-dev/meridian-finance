package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.correction.LoanCorrectionPlan;
import com.meridian.platform.loan.application.dto.correction.LoanCorrectionPlan.Task;
import com.meridian.platform.loan.application.dto.correction.CorrectionResponsibility;
import com.meridian.platform.loan.application.dto.correction.CorrectionScope;
import com.meridian.platform.loan.domain.model.LoanDocumentType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;

import java.util.HashSet;
import java.util.Set;

public class LoanCorrectionPlanPolicy {

    public void validate(LoanCorrectionPlan plan) {
        if (plan == null || plan.tasks() == null || plan.tasks().isEmpty() || plan.tasks().size() > 10) {
            throw invalid("A correction plan must contain between 1 and 10 tasks.");
        }
        Set<LoanDocumentType> createdDocumentTypes = new HashSet<>();
        Set<TaskTuple> tuples = new HashSet<>();
        for (Task task : plan.tasks()) {
            validateTask(task);
            if (task.createChecklistItem() && !createdDocumentTypes.add(task.documentType())) {
                throw invalid("A correction plan cannot create the same checklist item more than once.");
            }

            TaskTuple tuple = new TaskTuple(
                    task.responsibleParty(), task.scope(), task.documentType(),
                    task.checklistItemId(), task.baselineDocumentVersionId()
            );
            if (!tuples.add(tuple)) {
                throw invalid("Duplicate correction tasks are not allowed.");
            }
        }
    }

    private void validateTask(Task task) {
        if (task == null || task.scope() == null || task.responsibleParty() == null) {
            throw invalid("Correction task scope and responsibility are required.");
        }
        validateInstruction(task);
        switch (task.scope()) {
            case SUPPORTING_DOCUMENT_UPLOAD -> {
                if (task.documentType() == null
                        || !task.createChecklistItem()
                        || task.checklistItemId() != null
                        || task.baselineDocumentVersionId() != null) {
                    throw invalid("Supporting document upload task fields are invalid.");
                }
            }
            case DOCUMENT_REPLACEMENT -> {
                if (task.responsibleParty() != CorrectionResponsibility.CUSTOMER
                        || task.documentType() == null
                        || task.createChecklistItem()
                        || task.checklistItemId() == null
                        || task.baselineDocumentVersionId() == null) {
                    throw invalid("Document replacement task fields are invalid.");
                }
            }
            case DOCUMENT_REVIEW -> {
                if (task.responsibleParty() != CorrectionResponsibility.STAFF
                        || task.documentType() == null
                        || task.createChecklistItem()
                        || task.checklistItemId() == null
                        || task.baselineDocumentVersionId() == null) {
                    throw invalid("Document review task fields are invalid.");
                }
            }
            case APPLICATION_TERMS -> throw new BusinessRuleViolationException(
                    "CORRECTION_FIELD_NOT_ALLOWED",
                    "Requested amount and term are not editable during this checkpoint."
            );
        }
    }

    private void validateInstruction(Task task) {
        String customer = normalize(task.customerInstruction());
        String staff = normalize(task.staffInstruction());
        if (task.responsibleParty() == CorrectionResponsibility.CUSTOMER) {
            if (customer == null || staff != null) {
                throw invalid("Customer tasks require only a customer instruction.");
            }
            validatePlainText(customer);
        } else {
            if (staff == null || customer != null) {
                throw invalid("Staff tasks require only a staff instruction.");
            }
            validatePlainText(staff);
        }
    }

    private void validatePlainText(String value) {
        if (value.length() > 500 || value.chars().anyMatch(character ->
                Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t')) {
            throw invalid("Correction instructions must be plain text between 1 and 500 characters.");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private BusinessRuleViolationException invalid(String message) {
        return new BusinessRuleViolationException("INVALID_CORRECTION_PLAN", message);
    }

    private record TaskTuple(
            CorrectionResponsibility responsibility,
            CorrectionScope scope,
            LoanDocumentType documentType,
            java.util.UUID checklistItemId,
            java.util.UUID baselineVersionId
    ) {
    }
}
