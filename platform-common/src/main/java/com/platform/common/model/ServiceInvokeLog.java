package com.platform.common.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public record ServiceInvokeLog(
        String serviceCode,
        String consumerCode,
        String partnerCode,
        int status,
        long elapsedMillis,
        long responseSize,
        Instant createdAt
) {
    public ServiceInvokeLog(String serviceCode, String consumerCode, int status, long elapsedMillis, Instant createdAt) {
        this(serviceCode, consumerCode, null, status, elapsedMillis, 0L, createdAt);
    }

    public ServiceInvokeLog(String serviceCode, String consumerCode, String partnerCode, int status,
                            long elapsedMillis, Instant createdAt) {
        this(serviceCode, consumerCode, partnerCode, status, elapsedMillis, 0L, createdAt);
    }

    public static long bytesOf(String response) {
        return response == null ? 0L : response.getBytes(StandardCharsets.UTF_8).length;
    }
}
