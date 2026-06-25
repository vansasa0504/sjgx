package com.platform.partner.consumer;

public interface QuotaCounter {
    long incrementAndCheck(long consumerId, long maxRequests);
}
