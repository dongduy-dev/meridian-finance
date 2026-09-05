package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationReviewDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationReviewUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/loan-applications/{loanApplicationId}/review")
public class StaffLoanApplicationReviewController {

    private final QueryStaffLoanApplicationReviewUseCase queryReview;

    public StaffLoanApplicationReviewController(QueryStaffLoanApplicationReviewUseCase queryReview) {
        this.queryReview = queryReview;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:review')")
    public StaffLoanApplicationReviewDto query(@PathVariable UUID loanApplicationId) {
        return queryReview.query(loanApplicationId);
    }
}
