package com.platform.partner.consumer;

public record ConsumerQuota(long consumerId, long maxRequests, long warnThreshold, long usedRequests) {
    public ConsumerQuota used(long used) {
        return new ConsumerQuota(consumerId, maxRequests, warnThreshold, used);
    }
}
