package com.meridian.platform.audit.infrastructure.adapter.in.event;

import com.meridian.platform.audit.application.port.in.RecordAuditEventsUseCase;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BusinessAuditEventListener {

    static final String BUSINESS_OPERATION_MDC_KEY = "businessOperationId";

    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessAuditEventListener.class);

    private final RecordAuditEventsUseCase recordAuditEventsUseCase;

    public BusinessAuditEventListener(RecordAuditEventsUseCase recordAuditEventsUseCase) {
        this.recordAuditEventsUseCase = recordAuditEventsUseCase;
    }

    @EventListener
    public void onBusinessAuditEvent(BusinessAuditEvent event) {
        String previousBusinessOperationId = MDC.get(BUSINESS_OPERATION_MDC_KEY);
        MDC.put(BUSINESS_OPERATION_MDC_KEY, event.operationContext().operationId().toString());
        try {
            recordAuditEventsUseCase.record(event);
            LOGGER.atInfo()
                    .addKeyValue("actorType", event.operationContext().actorType().name())
                    .addKeyValue("entryCount", event.entries().size())
                    .log("Business audit recorded");
        } finally {
            restoreMdcValue(BUSINESS_OPERATION_MDC_KEY, previousBusinessOperationId);
        }
    }

    private static void restoreMdcValue(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }
}
