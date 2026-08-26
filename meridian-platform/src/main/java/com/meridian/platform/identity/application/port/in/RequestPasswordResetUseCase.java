package com.meridian.platform.identity.application.port.in;

import com.meridian.platform.identity.application.dto.PasswordResetRequest;

public interface RequestPasswordResetUseCase {

    void requestReset(PasswordResetRequest request);
}
