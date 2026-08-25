package com.meridian.platform.identity.application.port.in;

import com.meridian.platform.identity.application.dto.CurrentSessionLogoutCommand;

public interface LogoutUseCase {

    void logout(CurrentSessionLogoutCommand command);
}
