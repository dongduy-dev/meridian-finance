package com.meridian.platform.document.infrastructure.adapter.in.web;

import com.meridian.platform.document.application.dto.DocumentReviewDto;
import com.meridian.platform.document.application.dto.ReviewDocumentCommand;
import com.meridian.platform.document.application.dto.ReviewDocumentRequest;
import com.meridian.platform.document.application.port.in.ReviewDocumentUseCase;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loan-applications/{loanApplicationId}/document-review-items/{checklistItemId}")
public class DocumentReviewController {
    private final ReviewDocumentUseCase reviewDocumentUseCase;
    private final CurrentUserProvider currentUserProvider;

    public DocumentReviewController(
            ReviewDocumentUseCase reviewDocumentUseCase,
            CurrentUserProvider currentUserProvider
    ) {
        this.reviewDocumentUseCase = reviewDocumentUseCase;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/reviews")
    @PreAuthorize("hasAuthority('document:review')")
    public DocumentReviewDto review(
            @PathVariable UUID loanApplicationId,
            @PathVariable UUID checklistItemId,
            @Valid @RequestBody ReviewDocumentRequest request
    ) {
        AuthenticatedUser user = currentUserProvider.currentUser();
        return reviewDocumentUseCase.review(new ReviewDocumentCommand(
                loanApplicationId,
                checklistItemId,
                request.documentVersionId(),
                request.reviewRequestId(),
                request.outcome(),
                request.waiverReasonCode(),
                request.restrictedStaffNotes(),
                request.correctionReasonCode(),
                request.customerInstruction(),
                user.userId(),
                user.hasPermission("document:waive")
        ));
    }
}
