package com.platform.pipeline.ingest.adapter;

import com.platform.pipeline.ingest.HttpAdapter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolAdaptersTest {
    @Test
    void supportsHttpWebServiceApiGatewayAndFactory() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/data", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/data");
            assertEquals("{\"ok\":true}", new HttpAdapter().fetch(uri));
            assertEquals("{\"ok\":true}", new WebServiceAdapter().fetch(uri));
            assertEquals("{\"ok\":true}", new ApiGatewayAdapter().fetch(uri));
            ProtocolAdapterFactory factory = new ProtocolAdapterFactory(List.of(new HttpAdapter(), new WebServiceAdapter(), new ApiGatewayAdapter()));
            assertEquals("API_GW", factory.get("api_gw").protocol());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void supportsFileMessageAndDbAdapters() throws Exception {
        var temp = Files.createTempFile("sjgx", ".json");
        Files.writeString(temp, "{\"file\":\"ok\"}");
        assertEquals("{\"file\":\"ok\"}", new SftpAdapter().fetch(temp.toUri()));
        assertEquals("{\"file\":\"ok\"}", new FtpAdapter().fetch(temp.toUri()));

        KafkaAdapter kafka = new KafkaAdapter();
        kafka.put("topic-a", "[{\"id\":1}]");
        assertEquals("[{\"id\":1}]", kafka.fetch(URI.create("kafka:topic-a")));

        MqAdapter mq = new MqAdapter();
        mq.put("queue-a", "[{\"id\":2}]");
        assertEquals("[{\"id\":2}]", mq.fetch(URI.create("mq:queue-a")));

        DbAdapter db = new DbAdapter();
        db.put("selectCustomer", "[{\"id\":3}]");
        assertEquals("[{\"id\":3}]", db.fetch(URI.create("db:selectCustomer")));
    }
}
