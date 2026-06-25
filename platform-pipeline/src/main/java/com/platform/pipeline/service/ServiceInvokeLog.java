package com.platform.pipeline.service;

import java.time.Instant;

public record ServiceInvokeLog(String serviceCode, String consumerCode, int status, long elapsedMillis, Instant createdAt) {
}
