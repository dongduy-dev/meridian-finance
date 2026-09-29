package com.meridian.platform.customer.application.port.in;

import java.util.UUID;

public record CustomerDigitalAccessCandidate(UUID customerId, String displayName) {
}
