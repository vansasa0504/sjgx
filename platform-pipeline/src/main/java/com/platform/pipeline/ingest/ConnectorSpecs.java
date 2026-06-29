package com.platform.pipeline.ingest;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConnectorSpecs {
    private static final Map<String, ConnectorSpec> SPECS = Map.of(
            "HTTP", new ConnectorSpec("HTTP", List.of("JSON", "XML", "CSV"),
                    List.of("FULL", "INCREMENTAL", "SCHEDULED", "RESUME"), true, false),
            "WEBSERVICE", new ConnectorSpec("WEBSERVICE", List.of("XML", "JSON"),
                    List.of("FULL", "SCHEDULED"), true, false),
            "API_GW", new ConnectorSpec("API_GW", List.of("JSON"),
                    List.of("FULL", "REALTIME"), true, false),
            "FTP", new ConnectorSpec("FTP", List.of("CSV", "Excel"),
                    List.of("FULL", "SCHEDULED"), true, true),
            "SFTP", new ConnectorSpec("SFTP", List.of("CSV", "Excel"),
                    List.of("FULL", "SCHEDULED"), true, true),
            "KAFKA", new ConnectorSpec("KAFKA", List.of("JSON"),
                    List.of("REALTIME", "INCREMENTAL"), true, true),
            "MQ", new ConnectorSpec("MQ", List.of("JSON"),
                    List.of("REALTIME"), true, true),
            "DB", new ConnectorSpec("DB", List.of("JSON", "CSV"),
                    List.of("FULL", "INCREMENTAL", "SCHEDULED", "RESUME"), true, true)
    );

    private ConnectorSpecs() {
    }

    public static ConnectorSpec forProtocol(String protocol) {
        ConnectorSpec spec = SPECS.get(protocol.toUpperCase(Locale.ROOT));
        if (spec != null) {
            return spec;
        }
        return new ConnectorSpec(protocol.toUpperCase(Locale.ROOT), List.of("JSON"),
                List.of("FULL"), true, false);
    }
}
