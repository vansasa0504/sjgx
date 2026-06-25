package com.platform.pipeline.ingest.sync;

import java.util.List;

public class RealtimeSync extends IncrementalSync {
    @Override
    public String mode() { return "REALTIME"; }
}
