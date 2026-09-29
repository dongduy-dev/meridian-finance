package com.meridian.platform.identity.application.port.in;

import com.meridian.platform.identity.application.dto.CreateInternalUserRequest;
import com.meridian.platform.identity.application.dto.InternalUserDto;

import java.util.UUID;

public interface ProvisionInternalUserUseCase {
    InternalUserDto create(CreateInternalUserRequest request);

    void sendPasswordSetup(UUID userId);
}
