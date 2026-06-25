package com.platform.pipeline.ingest.adapter;

import com.platform.pipeline.ingest.ProtocolAdapter;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProtocolAdapterFactory {
    private final Map<String, ProtocolAdapter> adapters;

    public ProtocolAdapterFactory(Collection<ProtocolAdapter> adapters) {
        this.adapters = adapters.stream().collect(Collectors.toMap(a -> a.protocol().toUpperCase(Locale.ROOT), Function.identity()));
    }

    public ProtocolAdapter get(String protocol) {
        ProtocolAdapter adapter = adapters.get(protocol.toUpperCase(Locale.ROOT));
        if (adapter == null) {
            throw new IllegalArgumentException("unsupported protocol: " + protocol);
        }
        return adapter;
    }
}
