package com.meridian.platform.approval.domain.model;

public enum ReviewRecommendationAction {
    RECOMMEND_APPROVAL,
    RECOMMEND_REJECTION,
    RETURN_TO_CUSTOMER_REVISION,
    REQUEST_STAFF_CORRECTION;

    public boolean requiresReason() {
        return this != RECOMMEND_APPROVAL;
    }
}
