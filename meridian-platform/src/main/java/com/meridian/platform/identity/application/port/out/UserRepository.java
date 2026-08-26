package com.meridian.platform.identity.application.port.out;

import com.meridian.platform.identity.domain.model.User;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findByNormalizedEmail(String normalizedEmail);

    Optional<User> findByNormalizedEmailForUpdate(String normalizedEmail);

    Optional<User> findById(UUID userId);

    Optional<User> findByIdForUpdate(UUID userId);

    void createCustomerUser(User user);

    void updateLoginProtection(UUID userId, int failedLoginAttempts, Instant lockedUntil);

    void replacePasswordAndClearLoginProtection(UUID userId, String passwordHash);

    void markEmailVerified(UUID userId, Instant verifiedAt);
}
