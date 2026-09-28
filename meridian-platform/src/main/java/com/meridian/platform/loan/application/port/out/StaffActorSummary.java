package com.meridian.platform.loan.application.port.out;

import java.util.UUID;

public record StaffActorSummary(UUID userId, String displayName, String email) {
}
