package com.meridian.platform.identity.application.port.out;

public interface RefreshTokenCodecPort {

    GeneratedRefreshToken generate();

    String digest(String rawToken);
}
