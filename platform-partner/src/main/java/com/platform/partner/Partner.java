package com.platform.partner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Partner {
    private final long id;
    private final String name;
    private PartnerStatus status;
    private String rating;
    private final List<String> events = new ArrayList<>();
    private final Instant createdAt;

    public Partner(long id, String name) {
        this.id = id;
        this.name = name;
        this.status = PartnerStatus.REGISTERED;
        this.createdAt = Instant.now();
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
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
}
