package com.meridian.platform.shared.application.audit;

public interface BusinessAuditPublisher {

    void publish(BusinessAuditEvent event);
}
