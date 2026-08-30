package com.meridian.platform.document.application.port.in;

import com.meridian.platform.document.application.dto.CustomerDocumentChecklistDto;

import java.util.UUID;

public interface QueryOwnDocumentChecklistUseCase {

    CustomerDocumentChecklistDto query(UUID loanApplicationId);
}
