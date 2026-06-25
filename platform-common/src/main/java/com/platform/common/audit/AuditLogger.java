package com.platform.common.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public final class AuditLogger {
    private static final Logger LOGGER = Logger.getLogger(AuditLogger.class.getName());
    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private AuditLogger() {
    }

    public static void record(String action, String operator) {
        String event = Instant.now() + "|" + operator + "|" + action;
        EVENTS.add(event);
        LOGGER.info(event);
    }

    public static List<String> events() {
        return Collections.unmodifiableList(EVENTS);
    }

    public static void clear() {
        EVENTS.clear();
    }
}
