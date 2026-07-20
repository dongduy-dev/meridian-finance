package com.meridian.platform.loan.infrastructure.adapter.in.event;

import com.meridian.platform.document.application.event.DocumentUploadsCompletedEvent;
import com.meridian.platform.loan.application.port.in.CompleteDocumentUploadsUseCase;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentUploadsCompletedEventListener {

    private final CompleteDocumentUploadsUseCase completeDocumentUploadsUseCase;

    public DocumentUploadsCompletedEventListener(CompleteDocumentUploadsUseCase completeDocumentUploadsUseCase) {
        this.completeDocumentUploadsUseCase = completeDocumentUploadsUseCase;
    }

    @EventListener
    public void onDocumentUploadsCompleted(DocumentUploadsCompletedEvent event) {
        completeDocumentUploadsUseCase.completeDocumentUploads(
                event.loanApplicationId(),
                event.operationContext()
        );
    }
}
