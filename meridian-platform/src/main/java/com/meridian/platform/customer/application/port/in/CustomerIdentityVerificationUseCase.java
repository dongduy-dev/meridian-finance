package com.meridian.platform.customer.application.port.in;

import com.meridian.platform.customer.application.dto.CustomerIdentityDecisionRequest;
import com.meridian.platform.customer.application.dto.CustomerIdentityVerificationDto;
import com.meridian.platform.customer.application.port.out.CustomerIdentityEvidencePort;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public interface CustomerIdentityVerificationUseCase {
    List<CustomerIdentityVerificationDto> ownHistory();
    CustomerIdentityVerificationDto submitOwn(UUID requestId, UUID baselineVersionId, InputStream content, String mimeType, String filename);
    CustomerIdentityVerificationDto submitIntake(UUID caseId, UUID versionId);
    List<CustomerIdentityVerificationDto> pending(int page, int size);
    CustomerIdentityVerificationDto detail(UUID verificationId);
    CustomerIdentityVerificationDto decide(UUID verificationId, boolean verify, CustomerIdentityDecisionRequest request);
    CustomerIdentityEvidencePort.Content readOwn(UUID verificationId);
    CustomerIdentityEvidencePort.Content readStaff(UUID verificationId);
}
