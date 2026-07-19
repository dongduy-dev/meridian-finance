package com.meridian.platform.document.application.port.in;

import com.meridian.platform.document.application.dto.DocumentReviewDto;
import com.meridian.platform.document.application.dto.ReviewDocumentCommand;

public interface ReviewDocumentUseCase {
    DocumentReviewDto review(ReviewDocumentCommand command);
}
