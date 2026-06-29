package com.platform.pipeline.ingest;

public record ConnectorCheckResult(boolean ok, String message) {
    public static ConnectorCheckResult success() {
        return new ConnectorCheckResult(true, "OK");
    }

    public static ConnectorCheckResult failed(String message) {
        return new ConnectorCheckResult(false, message == null || message.isBlank() ? "connection failed" : message);
    }
}
