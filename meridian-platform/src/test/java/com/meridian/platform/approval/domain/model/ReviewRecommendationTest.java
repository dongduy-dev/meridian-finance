package com.meridian.platform.approval.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReviewRecommendationTest {

    @Test
    void recordsApprovalRecommendationWithoutReason() {
        ReviewRecommendation recommendation = ReviewRecommendation.recorded(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReviewRecommendationAction.RECOMMEND_APPROVAL,
                " ",
                "  reviewed  ",
                LocalDateTime.now()
        );

        assertNull(recommendation.reason());
        assertEquals("reviewed", recommendation.internalNotes());
    }

    @Test
    void requiresReasonForRejectionRecommendation() {
        BusinessRuleViolationException exception = assertThrows(
                BusinessRuleViolationException.class,
                () -> ReviewRecommendation.recorded(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ReviewRecommendationAction.RECOMMEND_REJECTION,
                        " ",
                        null,
                        LocalDateTime.now()
                )
        );

        assertEquals("RECOMMENDATION_REASON_REQUIRED", exception.getErrorCode());
    }

    @Test
    void trimsReasonForReturnRecommendation() {
        ReviewRecommendation recommendation = ReviewRecommendation.recorded(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReviewRecommendationAction.RETURN_TO_CUSTOMER_REVISION,
                "  missing document  ",
                null,
                LocalDateTime.now()
        );

        assertEquals("missing document", recommendation.reason());
    }
}
