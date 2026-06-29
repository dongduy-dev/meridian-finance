package com.meridian.platform.identity.application.port.in;

import com.meridian.platform.identity.application.dto.AuthResponse;
import com.meridian.platform.identity.application.dto.LoginRequest;

public interface AuthenticationUseCase {

    AuthResponse login(LoginRequest request);
}
