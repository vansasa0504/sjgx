package com.platform.pipeline.ingest;

import java.util.List;

public record RawDataBatch(List<String> records, long nextOffset) {
}
