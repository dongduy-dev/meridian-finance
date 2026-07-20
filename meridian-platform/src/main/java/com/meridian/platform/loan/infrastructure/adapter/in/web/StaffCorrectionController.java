package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.CorrectionResubmissionDto;
import com.meridian.platform.loan.application.dto.CorrectionResubmissionRequest;
import com.meridian.platform.loan.application.dto.StaffCorrectionTaskDto;
import com.meridian.platform.loan.application.port.in.CompleteStaffCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.in.QueryStaffCorrectionTasksUseCase;
import com.meridian.platform.loan.application.port.in.ResubmitStaffCorrectionUseCase;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/staff-corrections")
public class StaffCorrectionController {

    private final QueryStaffCorrectionTasksUseCase queryTasks;
    private final CompleteStaffCorrectionTaskUseCase completeTask;
    private final ResubmitStaffCorrectionUseCase resubmitCorrection;

    public StaffCorrectionController(
            QueryStaffCorrectionTasksUseCase queryTasks,
            CompleteStaffCorrectionTaskUseCase completeTask,
            ResubmitStaffCorrectionUseCase resubmitCorrection
    ) {
        this.queryTasks = queryTasks;
        this.completeTask = completeTask;
        this.resubmitCorrection = resubmitCorrection;
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('loan:correction:staff')")
    public List<StaffCorrectionTaskDto> findTasks(
            @RequestParam(defaultValue = "OPEN") LoanCorrectionTaskStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return queryTasks.findStaffTasks(status, page, size);
    }

    @PostMapping("/tasks/{taskId}/complete")
    @PreAuthorize("hasAuthority('loan:correction:staff')")
    public StaffCorrectionTaskDto complete(
            @PathVariable UUID taskId,
            @Valid @RequestBody CompleteCorrectionTaskRequest request
    ) {
        return completeTask.complete(taskId, request);
    }

    @PostMapping("/loan-applications/{loanApplicationId}/resubmit")
    @PreAuthorize("hasAuthority('loan:correction:staff')")
    public CorrectionResubmissionDto resubmit(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody CorrectionResubmissionRequest request
    ) {
        return resubmitCorrection.resubmitAsStaff(loanApplicationId, request);
    }
}
