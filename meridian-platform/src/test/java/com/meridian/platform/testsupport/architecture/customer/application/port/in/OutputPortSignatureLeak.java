package com.meridian.platform.testsupport.architecture.customer.application.port.in;

import com.meridian.platform.customer.application.port.out.CustomerIdentityEvidencePort;

public interface OutputPortSignatureLeak {
    CustomerIdentityEvidencePort.Content read();
}
