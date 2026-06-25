package com.platform.pipeline.ingest;

import com.platform.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngestServiceTest {
    @Test
    void fetchesHttpJsonAndStoresRawData() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/data", exchange -> {
            byte[] body = "[{\"id\":\"1\",\"name\":\"alpha\"},{\"id\":\"2\",\"name\":\"beta\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            RawDataRepository repository = new RawDataRepository();
            IngestService service = new IngestService(new HttpAdapter(), new JsonConverter(), repository);
            URI endpoint = URI.create("http://localhost:" + server.getAddress().getPort() + "/data");
            IngestTask task = service.createTask(1L, endpoint);

            service.testAndIngest(task);

            assertEquals(IngestTaskStatus.ONLINE, task.status());
            assertEquals(2, repository.findAll().size());
            assertEquals("alpha", repository.findAll().get(0).payload().get("name"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void controllerCreatesTaskRunsItAndReturnsRecords() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/data", exchange -> {
            byte[] body = "{\"id\":\"1\",\"name\":\"alpha\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            IngestService service = new IngestService(new HttpAdapter(), new JsonConverter(), new RawDataRepository());
            IngestController controller = new IngestController(service);
            String endpoint = "http://localhost:" + server.getAddress().getPort() + "/data";

            IngestTask task = controller.create(new IngestController.CreateIngestTaskRequest(1L, endpoint)).data();
            controller.run(task.id());

            assertEquals(1, controller.records().data().size());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsInvalidJsonPayload() {
        JsonConverter converter = new JsonConverter();

        assertThrows(BusinessException.class, () -> converter.convert("not-json"));
    }

    @Test
    void parsesEmptyArrayUnicodeAndNestedJsonSafely() {
        JsonConverter converter = new JsonConverter();

        assertEquals(0, converter.convert("[]").size());
        assertEquals("上海", converter.convert("{\"city\":\"上海\"}").get(0).get("city"));
        assertEquals("{score=99}", converter.convert("{\"meta\":{\"score\":99}}").get(0).get("meta"));
    }

    @Test
    void validatesTaskStateTransitions() {
        IngestTaskStateMachine machine = new IngestTaskStateMachine();

        assertEquals(IngestTaskStatus.TESTING, machine.transit(IngestTaskStatus.DRAFT, IngestTaskEvent.START_TEST));
        assertEquals(IngestTaskStatus.PENDING_APPROVAL, machine.transit(IngestTaskStatus.TESTING, IngestTaskEvent.SUBMIT_APPROVAL));
        assertEquals(IngestTaskStatus.ONLINE, machine.transit(IngestTaskStatus.PENDING_APPROVAL, IngestTaskEvent.APPROVE));
        assertEquals(IngestTaskStatus.OFFLINE, machine.transit(IngestTaskStatus.ONLINE, IngestTaskEvent.OFFLINE));
        assertThrows(BusinessException.class, () -> machine.transit(IngestTaskStatus.DRAFT, IngestTaskEvent.APPROVE));
    }
}
