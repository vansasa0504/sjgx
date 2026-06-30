package perf.datagen;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LoadDataGenerator {
    private static final int BATCH_SIZE = 1000;

    private LoadDataGenerator() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        String url = required(options, "url");
        String user = options.getOrDefault("user", "root");
        String password = options.getOrDefault("password", "");
        String table = options.getOrDefault("table", "invoke_log");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            switch (table) {
                case "invoke_log" -> generateInvokeLogs(connection, options);
                case "raw_data" -> generateRawData(connection, options);
                case "catalog" -> generateCatalog(connection, options);
                default -> throw new IllegalArgumentException("unsupported --table=" + table);
            }
            connection.commit();
        }
    }

    private static void generateInvokeLogs(Connection connection, Map<String, String> options) throws Exception {
        String service = options.getOrDefault("service", "svc-risk");
        String consumerPrefix = options.getOrDefault("consumer-prefix", "consumer-perf-");
        int months = integer(options, "months", 6);
        int perMonth = integer(options, "per-month", 50_000);
        boolean clean = Boolean.parseBoolean(options.getOrDefault("clean", "false"));
        if (clean) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM t_service_invoke_log WHERE service_code = ?")) {
                ps.setString(1, service);
                ps.executeUpdate();
            }
        }
        long id = nextId(connection, "t_service_invoke_log");
        LocalDate month = LocalDate.parse(options.getOrDefault("start-month", "2026-01-01")).withDayOfMonth(1);
        String sql = """
                INSERT INTO t_service_invoke_log
                (id, trace_id, service_code, consumer_code, partner_code, api_key, request_hash, status_code,
                 elapsed_millis, response_size, error_code, error_message, log_day, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int batched = 0;
            for (int m = 0; m < months; m++) {
                LocalDate base = month.plusMonths(m);
                for (int i = 0; i < perMonth; i++) {
                    Instant created = base.plusDays(i % 28).atStartOfDay().toInstant(ZoneOffset.UTC).plus(i % 86_400, ChronoUnit.SECONDS);
                    ps.setLong(1, id++);
                    ps.setString(2, "perf-" + UUID.randomUUID());
                    ps.setString(3, service);
                    ps.setString(4, consumerPrefix + (i % 100));
                    ps.setString(5, "partner-perf-" + (i % 20));
                    ps.setString(6, "ak-perf");
                    ps.setString(7, Integer.toHexString((service + i + created).hashCode()));
                    ps.setInt(8, i % 97 == 0 ? 500 : 200);
                    ps.setLong(9, 10 + (i % 120));
                    ps.setLong(10, 256 + (i % 4096));
                    ps.setString(11, i % 97 == 0 ? "SERVICE-500" : null);
                    ps.setString(12, i % 97 == 0 ? "synthetic perf error" : null);
                    ps.setString(13, created.toString().substring(0, 10).replace("-", ""));
                    ps.setTimestamp(14, Timestamp.from(created));
                    ps.addBatch();
                    if (++batched % BATCH_SIZE == 0) {
                        ps.executeBatch();
                    }
                }
            }
            ps.executeBatch();
        }
        System.out.printf("inserted invoke logs: %d%n", months * perMonth);
    }

    private static void generateRawData(Connection connection, Map<String, String> options) throws Exception {
        long taskId = Long.parseLong(options.getOrDefault("task-id", "1"));
        long partnerId = Long.parseLong(options.getOrDefault("partner-id", "1"));
        int count = integer(options, "count", 1_000_000);
        boolean clean = Boolean.parseBoolean(options.getOrDefault("clean", "false"));
        if (clean) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM t_raw_data WHERE task_id = ?")) {
                ps.setLong(1, taskId);
                ps.executeUpdate();
            }
        }
        long id = nextId(connection, "t_raw_data");
        String sql = """
                INSERT INTO t_raw_data (id, task_id, partner_id, batch_no, payload, quality_status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                ps.setLong(1, id++);
                ps.setLong(2, taskId);
                ps.setLong(3, partnerId);
                ps.setString(4, "perf-batch-" + (i / BATCH_SIZE));
                ps.setString(5, "{\"seq\":" + i + ",\"name\":\"perf-" + i + "\"}");
                ps.setString(6, "PASS");
                ps.setTimestamp(7, Timestamp.from(Instant.now().minusSeconds(count - i)));
                ps.addBatch();
                if ((i + 1) % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
        System.out.printf("inserted raw data: %d%n", count);
    }

    private static void generateCatalog(Connection connection, Map<String, String> options) throws Exception {
        int count = integer(options, "count", 10_000);
        boolean clean = Boolean.parseBoolean(options.getOrDefault("clean", "false"));
        if (clean) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM t_data_catalog WHERE catalog_code LIKE 'perf-%'")) {
                ps.executeUpdate();
            }
        }
        long id = nextId(connection, "t_data_catalog");
        String sql = """
                INSERT INTO t_data_catalog
                (id, catalog_code, name, subject, partner_id, data_type, scenario, field_definitions,
                 format, update_frequency, source, compliance_note, usage_limit, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                Timestamp now = Timestamp.from(Instant.now());
                ps.setLong(1, id++);
                ps.setString(2, "perf-" + i);
                ps.setString(3, "性能目录-" + i);
                ps.setString(4, "risk");
                ps.setLong(5, 1 + (i % 20));
                ps.setString(6, "API");
                ps.setString(7, i % 2 == 0 ? "risk-control" : "marketing");
                ps.setString(8, "[{\"name\":\"id\",\"type\":\"string\"}]");
                ps.setString(9, "JSON");
                ps.setString(10, "DAILY");
                ps.setString(11, "perf-generator");
                ps.setString(12, "synthetic data");
                ps.setString(13, "perf only");
                ps.setTimestamp(14, now);
                ps.setTimestamp(15, now);
                ps.addBatch();
                if ((i + 1) % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
        System.out.printf("inserted catalog rows: %d%n", count);
    }

    private static long nextId(Connection connection, String table) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COALESCE(MAX(id), 0) + 1 FROM " + table);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            int split = arg.indexOf('=');
            if (split < 0) {
                options.put(arg.substring(2), "true");
            } else {
                options.put(arg.substring(2, split), arg.substring(split + 1));
            }
        }
        return options;
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing --" + key);
        }
        return value;
    }

    private static int integer(Map<String, String> options, String key, int fallback) {
        return Integer.parseInt(options.getOrDefault(key, String.valueOf(fallback)));
    }
}
