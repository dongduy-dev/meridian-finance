package com.meridian.platform.identity.application.dto;

import com.meridian.platform.identity.domain.model.UserStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeInternalUserStatusRequest(@NotNull UserStatus status) {
}
