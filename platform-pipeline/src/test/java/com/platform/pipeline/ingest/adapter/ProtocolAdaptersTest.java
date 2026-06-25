package com.platform.pipeline.ingest.adapter;

import com.jcraft.jsch.JSch;
import com.platform.pipeline.ingest.HttpAdapter;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void ftpAdapterUsesCommonsNetClient() {
        FtpAdapter adapter = new FtpAdapter(() -> new FTPClient() {
            @Override
            public void connect(String hostname, int port) {
            }

            @Override
            public boolean login(String username, String password) {
                return "user".equals(username) && "pass".equals(password);
            }

            @Override
            public void enterLocalPassiveMode() {
            }

            @Override
            public boolean retrieveFile(String remote, OutputStream local) throws IOException {
                local.write("{\"ftp\":true}".getBytes(StandardCharsets.UTF_8));
                return "/data.json".equals(remote);
            }

            @Override
            public boolean isConnected() {
                return true;
            }

            @Override
            public void disconnect() {
            }
        });

        assertEquals("{\"ftp\":true}", adapter.fetch(URI.create("ftp://user:pass@localhost:21/data.json")));
    }

    @Test
    void sftpAdapterUsesJschClientAndReportsConnectionFailure() {
        SftpAdapter adapter = new SftpAdapter(new JSch(), 1);

        assertThrows(IllegalStateException.class, () ->
                adapter.fetch(URI.create("sftp://user:pass@127.0.0.1:1/data.json")));
    }

    @Test
    void kafkaMqAndDbAdaptersUseProductionClients() throws Exception {
        MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        TopicPartition partition = new TopicPartition("topic-a", 0);
        consumer.schedulePollTask(() -> {
            consumer.rebalance(List.of(partition));
            consumer.updateBeginningOffsets(Map.of(partition, 0L));
            consumer.addRecord(new ConsumerRecord<>("topic-a", 0, 0L, "k", "{\"id\":1}"));
        });
        KafkaAdapter kafka = new KafkaAdapter(() -> consumer, Duration.ofMillis(1));
        assertEquals("[{\"id\":1}]", kafka.fetch(URI.create("kafka:topic-a")));

        MqAdapter mq = new MqAdapter(new RabbitTemplate() {
            @Override
            public Object receiveAndConvert(String queueName) {
                return "queue-a".equals(queueName) ? "[{\"id\":2}]" : null;
            }
        });
        assertEquals("[{\"id\":2}]", mq.fetch(URI.create("mq:queue-a")));

        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:adapter;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create table customer(id int primary key, name varchar(20))");
            statement.execute("insert into customer(id, name) values(3, 'delta')");
        }
        DbAdapter db = new DbAdapter(Map.of("main", dataSource));
        assertEquals("[{\"id\":3,\"name\":\"delta\"}]",
                db.fetch(URI.create("db://main/select%20id%20as%20%22id%22,%20name%20as%20%22name%22%20from%20customer")));
    }
}
