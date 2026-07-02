package com.meridian.platform.approval.application.port.out;

import com.meridian.platform.approval.application.event.ApprovalDecisionRecordedEvent;

public interface ApprovalDecisionEventPublisher {

    void publish(ApprovalDecisionRecordedEvent event);
}
