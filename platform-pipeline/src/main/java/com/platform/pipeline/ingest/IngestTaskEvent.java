package com.platform.pipeline.ingest;

public enum IngestTaskEvent {
    START_TEST,
    FAIL_TEST,
    SUBMIT_APPROVAL,
    APPROVE,
    REJECT,
    OFFLINE
}
