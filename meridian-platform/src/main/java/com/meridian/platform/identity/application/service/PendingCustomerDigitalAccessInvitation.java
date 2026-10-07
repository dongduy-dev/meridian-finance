package com.meridian.platform.identity.application.service;

import com.meridian.platform.identity.application.dto.CustomerDigitalAccessDto;

record PendingCustomerDigitalAccessInvitation(CustomerDigitalAccessDto status, String recipientEmail,
                                              String rawVerificationToken, String rawSetupToken) {
}
