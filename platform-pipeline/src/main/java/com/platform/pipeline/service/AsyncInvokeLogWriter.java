package com.platform.pipeline.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AsyncInvokeLogWriter {
    private final List<ServiceInvokeLog> logs = new CopyOnWriteArrayList<>();

    public void write(ServiceInvokeLog log) {
        logs.add(log);
    }

    public List<ServiceInvokeLog> logs() {
        return new ArrayList<>(logs);
    }
}
