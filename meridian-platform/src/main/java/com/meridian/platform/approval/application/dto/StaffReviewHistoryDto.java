package com.meridian.platform.approval.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StaffReviewHistoryDto(UUID loanApplicationId, String applicationNumber, String applicationStatus,
                                   List<CycleDto> cycles) {
    public StaffReviewHistoryDto {
        cycles = List.copyOf(cycles);
    }

    public record CycleDto(UUID reviewCycleId, int cycleNumber, StaffRecommendationCaseDto.StaffActorDto assignedLoanOfficer,
                           String status, LocalDateTime startedAt, LocalDateTime endedAt,
                           RecommendationDto recommendation, DecisionDto decision) {
    }

    public record RecommendationDto(UUID recommendationId, UUID reviewCycleId, String action,
                                    String reason, String reasonCode,
                                    boolean internalNoteReadable,
                                    @JsonInclude(JsonInclude.Include.NON_NULL) String internalNotes,
                                    StaffRecommendationCaseDto.StaffActorDto recordedBy, LocalDateTime submittedAt) {
    }

    public record DecisionDto(UUID decisionId, UUID reviewRecommendationId, String action,
                              String reason, String reasonCode,
                              boolean internalNoteReadable,
                              @JsonInclude(JsonInclude.Include.NON_NULL) String internalNotes,
                              StaffRecommendationCaseDto.StaffActorDto recordedBy, LocalDateTime decidedAt) {
    }
}
