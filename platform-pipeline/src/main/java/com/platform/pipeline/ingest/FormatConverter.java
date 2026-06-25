package com.platform.pipeline.ingest;

import java.util.List;
import java.util.Map;

public interface FormatConverter {
    String format();

    List<Map<String, String>> convert(String rawPayload);
}
