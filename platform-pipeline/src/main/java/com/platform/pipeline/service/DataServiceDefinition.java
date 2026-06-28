package com.platform.pipeline.service;

import java.time.Instant;

public class DataServiceDefinition {
    private final long id;
    private final String serviceCode;
    private String name;
    private String routeKey;
    private DataServiceStatus status = DataServiceStatus.REGISTERED;
    private int version = 1;
    private final Instant createdAt = Instant.now();

    public DataServiceDefinition(long id, String serviceCode, String name, String routeKey) {
        this.id = id;
        this.serviceCode = serviceCode;
        this.name = name;
        this.routeKey = routeKey;
    }

    public long id() { return id; }
    public String serviceCode() { return serviceCode; }
    public String name() { return name; }
    public String routeKey() { return routeKey; }
    public DataServiceStatus status() { return status; }
    public void status(DataServiceStatus status) { this.status = status; }
    public int version() { return version; }
    public void incrementVersion() { this.version++; }
    public void restoreVersion(int version) { this.version = version; }
    public Instant createdAt() { return createdAt; }
    public void name(String name) { this.name = name; }
    public void routeKey(String routeKey) { this.routeKey = routeKey; }
}
