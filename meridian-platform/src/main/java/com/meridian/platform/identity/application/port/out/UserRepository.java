package com.meridian.platform.identity.application.port.out;

import com.meridian.platform.identity.domain.model.User;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findByNormalizedEmail(String normalizedEmail);
}
