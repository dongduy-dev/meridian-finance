package com.meridian.platform.identity.application.port.in;

import com.meridian.platform.identity.application.dto.EmailVerificationConfirmationRequest;

public interface ConfirmEmailVerificationUseCase {

    void confirmVerification(EmailVerificationConfirmationRequest request);
}
