package com.meridian.platform.shared.domain.audit;

public enum BusinessAuditPayloadKey {
    LOAN_APPLICATION_ID("loanApplicationId"),
    SALARY_ADVANCE_LIMIT_ID("salaryAdvanceLimitId"),
    MOVEMENT_TYPE("movementType"),
    REVIEW_RECOMMENDATION_ID("reviewRecommendationId"),
    REVIEW_RECOMMENDATION_ACTION("reviewRecommendationAction"),
    APPROVAL_DECISION_ACTION("approvalDecisionAction"),
    OFFER_STATUS("offerStatus"),
    EXPIRY_DISCOVERY_TRIGGER("expiryDiscoveryTrigger"),
    RESERVATION_RELEASE_TRIGGER("reservationReleaseTrigger");

    private final String jsonName;

    BusinessAuditPayloadKey(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }
}