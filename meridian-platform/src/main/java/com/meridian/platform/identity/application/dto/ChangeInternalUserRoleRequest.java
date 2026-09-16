package com.meridian.platform.identity.application.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeInternalUserRoleRequest(@NotNull Boolean assigned) {
}
