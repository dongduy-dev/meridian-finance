package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloseLoanAccountUseCaseTest {

    @Test
    void redactsRequestIdentityFromRendering() {
        UUID requestId = UUID.randomUUID();
        CloseLoanAccountUseCase.Command command =
                new CloseLoanAccountUseCase.Command(
                        requestId,
                        UUID.randomUUID()
                );

        assertFalse(command.toString().contains(requestId.toString()));
    }

    @Test
    void rejectsMissingIdentifiers() {
        UUID id = UUID.randomUUID();

        assertThrows(BusinessRuleViolationException.class, () ->
                new CloseLoanAccountUseCase.Command(null, id));
        assertThrows(BusinessRuleViolationException.class, () ->
                new CloseLoanAccountUseCase.Command(id, null));
    }
}
