package com.meridian.platform.identity.application.port.in;

import com.meridian.platform.identity.application.dto.PasswordResetConfirmationRequest;

public interface ConfirmPasswordResetUseCase {

    void confirmReset(PasswordResetConfirmationRequest request);
}
