package com.meridian.platform.identity.application.port.out;

import com.meridian.platform.identity.domain.model.User;

public interface TokenIssuerPort {

    IssuedAccessToken issueAccessToken(User user);
}
