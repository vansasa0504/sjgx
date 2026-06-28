package com.platform.common.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public record ServiceInvokeLog(
        String traceId,
        String serviceCode,
        String consumerCode,
        String partnerCode,
        String apiKey,
        String requestHash,
        int status,
        long elapsedMillis,
        long responseSize,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
    public ServiceInvokeLog(String serviceCode, String consumerCode, int status, long elapsedMillis, Instant createdAt) {
        this(null, serviceCode, consumerCode, null, null, null, status, elapsedMillis, 0L, null, null, createdAt);
    }

    public ServiceInvokeLog(String serviceCode, String consumerCode, String partnerCode, int status,
                            long elapsedMillis, Instant createdAt) {
        this(null, serviceCode, consumerCode, partnerCode, null, null, status, elapsedMillis, 0L, null, null, createdAt);
    }

    public ServiceInvokeLog(String serviceCode, String consumerCode, String partnerCode, int status,
                            long elapsedMillis, long responseSize, Instant createdAt) {
        this(null, serviceCode, consumerCode, partnerCode, null, null, status, elapsedMillis, responseSize, null, null, createdAt);
    }

    public String error() {
        if (errorCode == null || errorCode.isBlank()) {
            return errorMessage;
        }
        if (errorMessage == null || errorMessage.isBlank()) {
            return errorCode;
        }
        return errorCode + ": " + errorMessage;
    }

    public static long bytesOf(String response) {
        return response == null ? 0L : response.getBytes(StandardCharsets.UTF_8).length;
    }
}
