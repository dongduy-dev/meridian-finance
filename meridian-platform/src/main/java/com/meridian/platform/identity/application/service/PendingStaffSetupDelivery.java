package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.InternalUserDto;

record PendingStaffSetupDelivery(InternalUserDto user, String recipientEmail, String rawToken) {
}
