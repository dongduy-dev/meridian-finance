package com.meridian.platform.shared.domain.audit;

import java.util.Arrays;

public enum BusinessAuditPayloadKey {
    CUSTOMER_ID("customerId", ValueType.UUID),
    PROFILE_COMPLETION_STATUS("profileCompletionStatus", ValueType.CODE),
    CUSTOMER_BANK_ACCOUNT_ID("customerBankAccountId", ValueType.UUID),
    PREVIOUS_PRIMARY_BANK_ACCOUNT_ID("previousPrimaryBankAccountId", ValueType.UUID),
    NEW_PRIMARY_BANK_ACCOUNT_ID("newPrimaryBankAccountId", ValueType.UUID),
    BANK_ACCOUNT_STATUS("bankAccountStatus", ValueType.CODE),
    LOAN_APPLICATION_ID("loanApplicationId", ValueType.UUID),
    SALARY_ADVANCE_LIMIT_ID("salaryAdvanceLimitId", ValueType.UUID),
    MOVEMENT_TYPE("movementType", ValueType.CODE),
    REVIEW_RECOMMENDATION_ID("reviewRecommendationId", ValueType.UUID),
    REVIEW_RECOMMENDATION_ACTION("reviewRecommendationAction", ValueType.CODE),
    APPROVAL_DECISION_ACTION("approvalDecisionAction", ValueType.CODE),
    OFFER_STATUS("offerStatus", ValueType.CODE),
    EXPIRY_DISCOVERY_TRIGGER("expiryDiscoveryTrigger", ValueType.CODE),
    RESERVATION_RELEASE_TRIGGER("reservationReleaseTrigger", ValueType.CODE),
    DOCUMENT_CHECKLIST_ID("documentChecklistId", ValueType.UUID),
    DOCUMENT_CHECKLIST_ITEM_ID("documentChecklistItemId", ValueType.UUID),
    DOCUMENT_VERSION_ID("documentVersionId", ValueType.UUID),
    DOCUMENT_TYPE("documentType", ValueType.CODE),
    DOCUMENT_REVIEW_OUTCOME("documentReviewOutcome", ValueType.CODE),
    WAIVER_REASON_CODE("waiverReasonCode", ValueType.CODE),
    REVIEW_CYCLE_ID("reviewCycleId", ValueType.UUID),
    REVIEW_CYCLE_STATUS("reviewCycleStatus", ValueType.CODE),
    CORRECTION_REQUEST_ID("correctionRequestId", ValueType.UUID),
    CORRECTION_TASK_ID("correctionTaskId", ValueType.UUID),
    CORRECTION_REASON_CODE("correctionReasonCode", ValueType.CODE),
    RESUBMISSION_TARGET_STATUS("resubmissionTargetStatus", ValueType.CODE),
    LOAN_CONTRACT_ID("loanContractId", ValueType.UUID),
    LOAN_CONTRACT_STATUS("loanContractStatus", ValueType.CODE),
    CONTRACT_SUPERSESSION_REASON("contractSupersessionReason", ValueType.CODE),
    LOAN_ACCOUNT_ID("loanAccountId", ValueType.UUID),
    MANUAL_DISBURSEMENT_ID("manualDisbursementId", ValueType.UUID),
    REPAYMENT_SCHEDULE_ID("repaymentScheduleId", ValueType.UUID),
    PRODUCT_CODE("productCode", ValueType.CODE),
    PREVIOUS_APPLICATION_STATUS("previousApplicationStatus", ValueType.CODE),
    FINAL_APPLICATION_STATUS("finalApplicationStatus", ValueType.CODE),
    LOAN_ACCOUNT_STATUS("loanAccountStatus", ValueType.CODE),
    PARTNER_COMPANY_ID("partnerCompanyId", ValueType.UUID),
    PARTNER_COMPANY_STATUS("partnerCompanyStatus", ValueType.CODE),
    PARTNER_EMPLOYEE_IMPORT_BATCH_ID("partnerEmployeeImportBatchId", ValueType.UUID),
    PARTNER_EMPLOYEE_IMPORT_BATCH_STATUS("partnerEmployeeImportBatchStatus", ValueType.CODE),
    PREVIOUS_USER_STATUS("previousUserStatus", ValueType.CODE),
    FINAL_USER_STATUS("finalUserStatus", ValueType.CODE),
    ROLE_CODE("roleCode", ValueType.CODE),
    ASSISTED_ORIGINATION_CASE_ID("assistedOriginationCaseId", ValueType.UUID),
    ASSISTED_ORIGINATION_STATUS("assistedOriginationStatus", ValueType.CODE),
    INTAKE_DOCUMENT_VERSION_ID("intakeDocumentVersionId", ValueType.UUID),
    INTAKE_EVIDENCE_TYPE("intakeEvidenceType", ValueType.CODE);

    private final String jsonName;
    private final ValueType valueType;

    BusinessAuditPayloadKey(String jsonName, ValueType valueType) {
        this.jsonName = jsonName;
        this.valueType = valueType;
    }

    public String jsonName() {
        return jsonName;
    }

    public ValueType valueType() {
        return valueType;
    }

    public static BusinessAuditPayloadKey fromJsonName(String jsonName) {
        return Arrays.stream(values())
                .filter(key -> key.jsonName.equals(jsonName))
                .findFirst()
                .orElseThrow(() -> BusinessAuditPayload.invalidPayload(
                        "Audit payload key is not allowed."
                ));
    }

    public enum ValueType {
        UUID,
        CODE
    }
}
