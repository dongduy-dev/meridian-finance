package com.meridian.platform.shared.domain.audit;

import java.util.Arrays;

public enum BusinessAuditPayloadKey {
    CUSTOMER_ID("customerId", ValueType.UUID),
    PROFILE_COMPLETION_STATUS("profileCompletionStatus", ValueType.CODE),
    LOAN_APPLICATION_ID("loanApplicationId", ValueType.UUID),
    SALARY_ADVANCE_LIMIT_ID("salaryAdvanceLimitId", ValueType.UUID),
    MOVEMENT_TYPE("movementType", ValueType.CODE),
    REVIEW_RECOMMENDATION_ID("reviewRecommendationId", ValueType.UUID),
    REVIEW_RECOMMENDATION_ACTION("reviewRecommendationAction", ValueType.CODE),
    APPROVAL_DECISION_ACTION("approvalDecisionAction", ValueType.CODE),
    OFFER_STATUS("offerStatus", ValueType.CODE),
    EXPIRY_DISCOVERY_TRIGGER("expiryDiscoveryTrigger", ValueType.CODE),
    RESERVATION_RELEASE_TRIGGER("reservationReleaseTrigger", ValueType.CODE);

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
