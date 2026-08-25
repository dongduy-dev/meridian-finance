package com.meridian.platform.identity.application.port.in;

import com.meridian.platform.identity.application.dto.EmailVerificationRequest;

public interface RequestEmailVerificationUseCase {

    void requestVerification(EmailVerificationRequest request);
}
