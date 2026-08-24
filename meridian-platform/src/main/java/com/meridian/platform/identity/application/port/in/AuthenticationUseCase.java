package com.meridian.platform.identity.application.port.in;

import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.dto.LoginRequest;

public interface AuthenticationUseCase {

    AuthenticationResult login(LoginRequest request);

    AuthenticationResult refresh(String refreshToken);
}
