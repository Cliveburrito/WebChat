package com.example.WebChat.observability;

import org.slf4j.MDC;

public final class RequestTracking {
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String START_NANOS_ATTRIBUTE = RequestTracking.class.getName() + ".startNanos";
    public static final String AUTH_FAILURE_REASON_ATTRIBUTE = RequestTracking.class.getName() + ".authFailureReason";

    private RequestTracking() {
    }

    public static String currentRequestId() {
        return MDC.get(REQUEST_ID_MDC_KEY);
    }
}
