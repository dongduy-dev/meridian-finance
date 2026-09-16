package com.meridian.platform.identity.application.port.out;

import java.util.OptionalLong;
import java.util.UUID;

public interface AuthorizationVersionRepository {

    OptionalLong findAuthorizationVersion(UUID userId);
}
