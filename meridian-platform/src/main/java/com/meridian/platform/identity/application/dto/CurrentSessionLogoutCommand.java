package com.meridian.platform.identity.application.dto;

import java.util.Objects;
import java.util.Optional;

public record CurrentSessionLogoutCommand(
        Optional<String> refreshToken,
        Optional<AccessTokenReference> accessToken
) {

    public CurrentSessionLogoutCommand {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        Objects.requireNonNull(accessToken, "accessToken must not be null");
    }
}
