package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.document.application.port.out.LoanAssistedOriginationPort;
import com.meridian.platform.loan.application.port.in.AuthorizeAssistedOriginationEvidenceUseCase;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AssistedOriginationEvidenceAuthorizationAdapter implements LoanAssistedOriginationPort {

    private final AuthorizeAssistedOriginationEvidenceUseCase useCase;

    public AssistedOriginationEvidenceAuthorizationAdapter(
            AuthorizeAssistedOriginationEvidenceUseCase useCase
    ) {
        this.useCase = useCase;
    }

    @Override
    public AuthorizedIntake authorizeRead(UUID caseId) {
        var authorized = useCase.authorizeEvidenceRead(caseId);
        return new AuthorizedIntake(authorized.caseId(), authorized.productCode());
    }

    @Override
    public AuthorizedIntake authorizeMutation(UUID caseId) {
        var authorized = useCase.authorizeEvidenceMutation(caseId);
        return new AuthorizedIntake(authorized.caseId(), authorized.productCode());
    }
}
