package com.meridian.platform.identity.application.port.in;

import java.util.UUID;

/** Purpose-limited internal classification; Customer login identity is never included. */
public record WorkflowActorSummary(
        UUID userId, String userType, UUID customerId, StaffActorSummary staff
) {
}
