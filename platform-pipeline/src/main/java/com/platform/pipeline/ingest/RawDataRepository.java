package com.platform.pipeline.ingest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RawDataRepository {
    private final List<RawDataRecord> records = new ArrayList<>();

    public void saveAll(List<RawDataRecord> newRecords) {
        records.addAll(newRecords);
    }

    public List<RawDataRecord> findAll() {
        return Collections.unmodifiableList(records);
    }
}
