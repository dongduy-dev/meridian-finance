package com.meridian.platform.loan.infrastructure.adapter.in.document;

import com.meridian.platform.document.application.spi.loan.LoanAssistedActionAuthorizationSpi;
import com.meridian.platform.loan.application.port.in.AuthorizeAssistedActionEvidenceUseCase;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AssistedActionEvidenceAuthorizationAdapter implements LoanAssistedActionAuthorizationSpi {

    private final AuthorizeAssistedActionEvidenceUseCase useCase;

    public AssistedActionEvidenceAuthorizationAdapter(AuthorizeAssistedActionEvidenceUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void authorizeOfferEvidence(UUID loanApplicationId, UUID approvedOfferId, String declaredDecision) {
        useCase.authorizeOfferEvidence(loanApplicationId, approvedOfferId, declaredDecision);
    }

    @Override
    public void authorizeContractEvidence(UUID loanApplicationId, UUID loanContractId, int contractVersion) {
        useCase.authorizeContractEvidence(loanApplicationId, loanContractId, contractVersion);
    }

    @Override
    public void authorizeCancellationEvidence(UUID loanApplicationId, UUID correctionRequestId) {
        useCase.authorizeCancellationEvidence(loanApplicationId, correctionRequestId);
    }
}
