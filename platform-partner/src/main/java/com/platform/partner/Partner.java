package com.platform.partner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Partner {
    private final long id;
    private final String name;
    private String dataType;
    private String industry;
    private String complianceLevel;
    private PartnerStatus status;
    private String rating;
    private final List<String> events = new ArrayList<>();
    private final Instant createdAt;

    public Partner(long id, String name) {
        this(id, name, null, null, null);
    }

    public Partner(long id, String name, String dataType, String industry, String complianceLevel) {
        this.id = id;
        this.name = name;
        this.dataType = dataType;
        this.industry = industry;
        this.complianceLevel = complianceLevel;
        this.status = PartnerStatus.REGISTERED;
        this.createdAt = Instant.now();
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String dataType() {
        return dataType;
    }

    public String industry() {
        return industry;
    }

    public String complianceLevel() {
        return complianceLevel;
    }

    public PartnerStatus status() {
        return status;
    }

    void status(PartnerStatus status) {
        this.status = status;
    }

    public String rating() {
        return rating;
    }

    void rating(String rating) {
        this.rating = rating;
    }

    public List<String> events() {
        return events;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public void update(String dataType, String industry, String complianceLevel) {
        if (dataType != null) {
            this.dataType = dataType;
        }
        if (industry != null) {
            this.industry = industry;
        }
        if (complianceLevel != null) {
            this.complianceLevel = complianceLevel;
        }
    }
}
