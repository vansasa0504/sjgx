package com.platform.pipeline.ingest;

import com.platform.common.model.Result;
import com.platform.common.security.RequirePermission;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingest")
public class ConnectorController {
    private final IngestService ingestService;

    public ConnectorController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @GetMapping("/connectors")
    @RequirePermission("ingest:view")
    public Result<List<ConnectorSpec>> connectors() {
        return Result.ok(ingestService.connectorSpecs());
    }
}
