package com.platform.pipeline.storage.lifecycle;

import com.platform.pipeline.storage.etl.DataAsset;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DataLifecycleManager {
    private final LifecyclePolicy policy;
    private final Clock clock;
    private final List<LifecycleEvent> events = new ArrayList<>();

    public DataLifecycleManager(LifecyclePolicy policy) {
        this(policy, Clock.systemUTC());
    }

    public DataLifecycleManager(LifecyclePolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    public LifecycleAction scan(DataAsset asset) {
        Duration age = Duration.between(asset.createdAt(), clock.instant());
        LifecycleAction action = LifecycleAction.KEEP;
        if (age.compareTo(policy.destroyAfter()) >= 0) {
            action = LifecycleAction.DESTROY;
        } else if (age.compareTo(policy.archiveAfter()) >= 0) {
            action = LifecycleAction.ARCHIVE;
        }
        events.add(new LifecycleEvent(asset.assetCode(), action, Instant.now(clock)));
        return action;
    }

    public List<LifecycleEvent> events() {
        return List.copyOf(events);
    }
}
