package com.platform.partner.consumer;

import java.time.Instant;

public class Consumer {
    private final long id;
    private final String consumerCode;
    private final String name;
    private final String businessLine;
    private final String systemType;
    private final String complianceLevel;
    private ConsumerStatus status = ConsumerStatus.REGISTERED;
    private final Instant createdAt = Instant.now();

    public Consumer(long id, String consumerCode, String name, String businessLine, String systemType, String complianceLevel) {
        this.id = id;
        this.consumerCode = consumerCode;
        this.name = name;
        this.businessLine = businessLine;
        this.systemType = systemType;
        this.complianceLevel = complianceLevel;
    }

    public long id() { return id; }
    public String consumerCode() { return consumerCode; }
    public String name() { return name; }
    public String businessLine() { return businessLine; }
    public String systemType() { return systemType; }
    public String complianceLevel() { return complianceLevel; }
    public ConsumerStatus status() { return status; }
    public void status(ConsumerStatus status) { this.status = status; }
    public Instant createdAt() { return createdAt; }
}
