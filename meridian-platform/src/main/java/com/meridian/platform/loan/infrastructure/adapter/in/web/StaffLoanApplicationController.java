package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.StaffLoanApplicationCaseDto;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationFilters;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationPageDto;
import com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationsUseCase;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/staff/loan-applications")
public class StaffLoanApplicationController {

    private final QueryStaffLoanApplicationsUseCase queryStaffLoanApplications;

    public StaffLoanApplicationController(
            QueryStaffLoanApplicationsUseCase queryStaffLoanApplications
    ) {
        this.queryStaffLoanApplications = queryStaffLoanApplications;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('loan:read')")
    public StaffLoanApplicationPageDto queryApplications(
            @RequestParam(required = false) ProductCode productCode,
            @RequestParam(required = false) LoanApplicationStatus status,
            @RequestParam(required = false) @Size(max = 50) String applicationNumber,
            @RequestParam(required = false) OriginationChannel originationChannel,
            @RequestParam(required = false) String submittedFrom,
            @RequestParam(required = false) String submittedTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return queryStaffLoanApplications.queryApplications(new StaffLoanApplicationFilters(
                productCode, status, applicationNumber, originationChannel,
                parseSubmissionDate(submittedFrom), parseSubmissionDate(submittedTo)), page, size);
    }

    private static LocalDate parseSubmissionDate(String value) {
        if (value == null || value.isEmpty()) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException("Submission dates must use YYYY-MM-DD.");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Enter a valid submission date.");
        }
    }

    @GetMapping("/{loanApplicationId}")
    @PreAuthorize("hasAuthority('loan:read')")
    public StaffLoanApplicationCaseDto queryCase(
            @PathVariable UUID loanApplicationId
    ) {
        return queryStaffLoanApplications.queryCase(loanApplicationId);
    }
}
