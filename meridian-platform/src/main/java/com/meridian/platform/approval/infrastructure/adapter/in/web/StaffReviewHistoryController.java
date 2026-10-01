package com.meridian.platform.approval.infrastructure.adapter.in.web;

import com.meridian.platform.approval.application.dto.StaffReviewHistoryDto;
import com.meridian.platform.approval.application.port.in.QueryStaffReviewHistoryUseCase;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/loan-applications")
public class StaffReviewHistoryController {
    private final QueryStaffReviewHistoryUseCase history;

    public StaffReviewHistoryController(QueryStaffReviewHistoryUseCase history) {
        this.history = history;
    }

    @GetMapping("/{loanApplicationId}/review-history")
    @PreAuthorize("hasAnyAuthority('loan:review', 'approval:recommend', 'approval:decide')")
    public ResponseEntity<StaffReviewHistoryDto> query(@PathVariable UUID loanApplicationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(history.query(loanApplicationId));
    }
}
