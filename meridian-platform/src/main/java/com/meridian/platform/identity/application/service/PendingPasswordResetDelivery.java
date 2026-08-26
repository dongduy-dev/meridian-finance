package com.meridian.platform.identity.application.service;

record PendingPasswordResetDelivery(String recipientEmail, String rawToken) {
}
