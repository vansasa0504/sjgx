package com.platform.pipeline.ingest.sync;

public class ScheduledSync extends IncrementalSync {
    @Override
    public String mode() { return "SCHEDULED"; }
}
