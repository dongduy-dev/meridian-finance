package com.meridian.platform.identity.application.service;

record PendingEmailVerificationDelivery(String recipientEmail, String rawToken) {
}
