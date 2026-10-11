package com.meridian.platform.identity.application.port.in;

public interface BootstrapAdministratorUseCase {
    void bootstrap(String email, String displayName);
}
