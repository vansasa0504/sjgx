package com.platform.pipeline.ingest;

import com.platform.common.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ingest/tasks")
public class IngestController {
    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping
    public Result<IngestTask> create(@RequestBody CreateIngestTaskRequest request) {
        return Result.ok(ingestService.createTask(request.partnerId(), URI.create(request.endpoint())));
    }

    @PostMapping("/{id}/test")
    public Result<List<RawDataRecord>> run(@PathVariable long id) {
        return Result.ok(ingestService.run(id));
    }

    @GetMapping("/records")
    public Result<List<RawDataRecord>> records() {
        return Result.ok(ingestService.records());
    }

    public record CreateIngestTaskRequest(long partnerId, String endpoint) {
    }
}
