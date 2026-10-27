package com.meridian.platform.testsupport.architecture.customer.infrastructure.adapter.in.web;

import com.meridian.platform.customer.application.port.out.CustomerIdentityEvidencePort;

public class OutputPortSignatureLeak {
    private void content(CustomerIdentityEvidencePort.Content content) {}
}
