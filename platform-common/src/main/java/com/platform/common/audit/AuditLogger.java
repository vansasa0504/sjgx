package com.platform.common.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AuditLogger {
    private static final List<String> EVENTS = new ArrayList<>();

    private AuditLogger() {
    }

    public static void record(String action, String operator) {
        EVENTS.add(Instant.now() + "|" + operator + "|" + action);
    }

    public static List<String> events() {
        return Collections.unmodifiableList(EVENTS);
    }

    public static void clear() {
        EVENTS.clear();
    }
}
