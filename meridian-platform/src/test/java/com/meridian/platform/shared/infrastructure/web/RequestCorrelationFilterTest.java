package com.meridian.platform.shared.infrastructure.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesCanonicalRequestIdAndMakesItAvailableDownstream() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            String downstreamValue = MDC.get(RequestCorrelationFilter.REQUEST_CORRELATION_MDC_KEY);
            assertCanonicalUuid(downstreamValue);
            assertEquals(
                    downstreamValue,
                    ((HttpServletResponse) servletResponse).getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER)
            );
        });

        assertCanonicalUuid(response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get(RequestCorrelationFilter.REQUEST_CORRELATION_MDC_KEY));
    }

    @Test
    void propagatesValidIncomingRequestIdAndRestoresPriorMdcValue() throws Exception {
        String incomingRequestId = "7ec45d1b-4238-4764-8814-9138275ce346";
        MockHttpServletRequest request = request();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, incomingRequestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(RequestCorrelationFilter.REQUEST_CORRELATION_MDC_KEY, "outer-request");

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertEquals(incomingRequestId, MDC.get(RequestCorrelationFilter.REQUEST_CORRELATION_MDC_KEY))
        );

        assertEquals(incomingRequestId, response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER));
        assertEquals("outer-request", MDC.get(RequestCorrelationFilter.REQUEST_CORRELATION_MDC_KEY));
    }

    @Test
    void replacesMalformedIncomingValueWithoutPuttingItInMdc() throws Exception {
        String malformedRequestId = "caller-controlled\r\ntext";
        MockHttpServletRequest request = request();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, malformedRequestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            String downstreamValue = MDC.get(RequestCorrelationFilter.REQUEST_CORRELATION_MDC_KEY);
            assertNotEquals(malformedRequestId, downstreamValue);
            assertCanonicalUuid(downstreamValue);
        });

        String effectiveRequestId = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertNotEquals(malformedRequestId, effectiveRequestId);
        assertCanonicalUuid(effectiveRequestId);
        assertNull(MDC.get(RequestCorrelationFilter.REQUEST_CORRELATION_MDC_KEY));
    }

    @Test
    void sequentialRequestsCannotInheritAnotherRequestsCorrelationId() throws Exception {
        List<String> observedValues = new ArrayList<>();

        for (int requestNumber = 0; requestNumber < 2; requestNumber++) {
            filter.doFilter(request(), new MockHttpServletResponse(), (request, response) ->
                    observedValues.add(MDC.get(RequestCorrelationFilter.REQUEST_CORRELATION_MDC_KEY))
            );
            assertNull(MDC.get(RequestCorrelationFilter.REQUEST_CORRELATION_MDC_KEY));
        }

        assertEquals(2, observedValues.size());
        assertNotEquals(observedValues.get(0), observedValues.get(1));
        observedValues.forEach(RequestCorrelationFilterTest::assertCanonicalUuid);
    }

    @Test
    void completionLogUsesCorrelationAndExcludesQueryString() throws Exception {
        String requestCorrelationId = "89861da5-6c4e-4b7e-a12f-1be77faed76c";
        MockHttpServletRequest request = request();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, requestCorrelationId);
        request.setQueryString("sensitive=value");
        Logger logger = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> { });
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }

        assertEquals(1, appender.list.size());
        ILoggingEvent loggingEvent = appender.list.getFirst();
        assertEquals("HTTP request completed", loggingEvent.getFormattedMessage());
        assertEquals(
                requestCorrelationId,
                loggingEvent.getMDCPropertyMap().get(RequestCorrelationFilter.REQUEST_CORRELATION_MDC_KEY)
        );
        assertEquals("GET", keyValue(loggingEvent, "httpMethod"));
        assertEquals("/api/v1/health", keyValue(loggingEvent, "requestPath"));
        assertEquals("200", keyValue(loggingEvent, "httpStatus"));
        assertNotNull(keyValue(loggingEvent, "durationMs"));
        assertFalse(loggingEvent.toString().contains("sensitive=value"));
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/health");
    }

    private static void assertCanonicalUuid(String value) {
        assertNotNull(value);
        assertEquals(UUID.fromString(value).toString(), value);
    }

    private static String keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .map(pair -> String.valueOf(pair.value))
                .findFirst()
                .orElse(null);
    }
}
