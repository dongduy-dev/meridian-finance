package com.meridian.platform.loan.infrastructure.adapter.in.web;

import com.meridian.platform.loan.application.dto.CompleteCorrectionTaskRequest;
import com.meridian.platform.loan.application.dto.CorrectionResubmissionDto;
import com.meridian.platform.loan.application.dto.CorrectionResubmissionRequest;
import com.meridian.platform.loan.application.dto.CustomerCorrectionTaskDto;
import com.meridian.platform.loan.application.port.in.CompleteOwnCorrectionTaskUseCase;
import com.meridian.platform.loan.application.port.in.QueryOwnCorrectionTasksUseCase;
import com.meridian.platform.loan.application.port.in.ResubmitOwnCorrectionUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/corrections")
public class CustomerCorrectionController {
    private final QueryOwnCorrectionTasksUseCase queryTasks;
    private final CompleteOwnCorrectionTaskUseCase completeTask;
    private final ResubmitOwnCorrectionUseCase resubmitCorrection;

    public CustomerCorrectionController(
            QueryOwnCorrectionTasksUseCase queryTasks,
            CompleteOwnCorrectionTaskUseCase completeTask,
            ResubmitOwnCorrectionUseCase resubmitCorrection
    ) {
        this.queryTasks = queryTasks;
        this.completeTask = completeTask;
        this.resubmitCorrection = resubmitCorrection;
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('loan:correction:own')")
    public List<CustomerCorrectionTaskDto> findOwnTasks(@PathVariable UUID loanApplicationId) {
        return queryTasks.findOwnTasks(loanApplicationId);
    }

    @PostMapping("/tasks/{taskId}/complete")
    @PreAuthorize("hasAuthority('loan:correction:own')")
    public CustomerCorrectionTaskDto completeOwnTask(
            @PathVariable UUID loanApplicationId,
            @PathVariable UUID taskId,
            @Valid @RequestBody CompleteCorrectionTaskRequest request
    ) {
        return completeTask.complete(loanApplicationId, taskId, request);
    }

    @PostMapping("/resubmit")
    @PreAuthorize("hasAuthority('loan:correction:own')")
    public CorrectionResubmissionDto resubmit(
            @PathVariable UUID loanApplicationId,
            @Valid @RequestBody CorrectionResubmissionRequest request
    ) {
        return resubmitCorrection.resubmit(loanApplicationId, request);
    }
}
