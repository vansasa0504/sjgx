package com.platform.common.log;

import com.platform.common.db.IdGenerator;
import com.platform.common.model.Page;
import com.platform.common.model.ServiceInvokeLog;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class JdbcServiceInvokeLogRepository {
    private static final DateTimeFormatter LOG_DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcServiceInvokeLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    public ServiceInvokeLog save(ServiceInvokeLog log) {
        ServiceInvokeLog normalized = normalize(log);
        jdbcTemplate.update("""
                INSERT INTO t_service_invoke_log
                (id, trace_id, service_code, consumer_code, partner_code, api_key, request_hash, status_code,
                 elapsed_millis, response_size, error_code, error_message, log_day, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                idGenerator.nextId("t_service_invoke_log"), normalized.traceId(), normalized.serviceCode(),
                normalized.consumerCode(), normalized.partnerCode(), normalized.apiKey(), normalized.requestHash(),
                normalized.status(), normalized.elapsedMillis(), normalized.responseSize(), normalized.errorCode(),
                normalized.errorMessage(), LOG_DAY.format(normalized.createdAt()), Timestamp.from(normalized.createdAt()));
        return normalized;
    }

    /**
     * Only for legacy tests and in-memory parity checks. Production callers must use range queries.
     */
    @Deprecated
    public List<ServiceInvokeLog> findAll() {
        return jdbcTemplate.query("SELECT * FROM t_service_invoke_log ORDER BY created_at DESC, id DESC", mapper());
    }

    public Page<ServiceInvokeLog> findByRange(Instant from, Instant to, int page, int size) {
        return queryFiltered(null, null, null, from, to, page, size);
    }

    public Page<ServiceInvokeLog> findByServiceRange(String serviceCode, String consumerCode, String status,
                                                     Instant from, Instant to, int page, int size) {
        return queryFiltered(serviceCode, consumerCode, status, from, to, page, size);
    }

    public List<ServiceInvokeLog> findAllByRange(Instant from, Instant to) {
        List<ServiceInvokeLog> records = new ArrayList<>();
        int page = 1;
        while (true) {
            Page<ServiceInvokeLog> current = findByRange(from, to, page, 1000);
            records.addAll(current.records());
            if (records.size() >= current.total() || current.records().isEmpty()) {
                return records;
            }
            page++;
        }
    }

    public Page<ServiceInvokeLog> findByService(String serviceCode, String consumerCode, String status, int page, int size) {
        return queryFiltered(serviceCode, consumerCode, status, null, null, page, size);
    }

    public Page<ServiceInvokeLog> findByConsumer(String consumerCode, int page, int size) {
        return queryFiltered(null, consumerCode, null, null, null, page, size);
    }

    private Page<ServiceInvokeLog> queryFiltered(String serviceCode, String consumerCode, String status,
                                                 Instant from, Instant to, int page, int size) {
        int safeSize = size <= 0 ? 10 : size;
        int safePage = page <= 0 ? 1 : page;
        int offset = (safePage - 1) * safeSize;

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (serviceCode != null && !serviceCode.isBlank()) {
            where.append(" AND service_code = ?");
            args.add(serviceCode);
        }
        if (consumerCode != null && !consumerCode.isBlank()) {
            where.append(" AND consumer_code = ?");
            args.add(consumerCode);
        }
        Integer statusCode = null;
        if (status != null && !status.isBlank()) {
            try {
                statusCode = Integer.parseInt(status);
                where.append(" AND status_code = ?");
                args.add(statusCode);
            } catch (NumberFormatException ignored) {
                // 非数字 status 不作为过滤条件
            }
        }
        if (from != null) {
            where.append(" AND created_at >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            where.append(" AND created_at < ?");
            args.add(Timestamp.from(to));
        }

        String orderClause = " ORDER BY created_at DESC, id DESC";
        String dataSql = "SELECT * FROM t_service_invoke_log" + where + orderClause + " LIMIT ? OFFSET ?";
        Object[] dataArgs = appendArgs(args.toArray(), safeSize, offset);
        List<ServiceInvokeLog> records = jdbcTemplate.query(dataSql, mapper(), dataArgs);

        String countSql = "SELECT COUNT(*) FROM t_service_invoke_log" + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        return Page.of(records, total == null ? 0L : total, safePage, safeSize);
    }

    private Object[] appendArgs(Object[] args, int limit, int offset) {
        Object[] result = new Object[args.length + 2];
        System.arraycopy(args, 0, result, 0, args.length);
        result[args.length] = limit;
        result[args.length + 1] = offset;
        return result;
    }

    private ServiceInvokeLog normalize(ServiceInvokeLog log) {
        Instant createdAt = log.createdAt() == null ? Instant.now() : log.createdAt();
        return new ServiceInvokeLog(log.traceId(), log.serviceCode(), log.consumerCode(), log.partnerCode(),
                log.apiKey(), log.requestHash(), log.status(), log.elapsedMillis(), log.responseSize(),
                log.errorCode(), log.errorMessage(), createdAt);
    }

    private RowMapper<ServiceInvokeLog> mapper() {
        return new RowMapper<>() {
            @Override
            public ServiceInvokeLog mapRow(ResultSet rs, int rowNum) throws SQLException {
                Timestamp createdAt = rs.getTimestamp("created_at");
                return new ServiceInvokeLog(
                        rs.getString("trace_id"),
                        rs.getString("service_code"),
                        rs.getString("consumer_code"),
                        rs.getString("partner_code"),
                        rs.getString("api_key"),
                        rs.getString("request_hash"),
                        rs.getInt("status_code"),
                        rs.getLong("elapsed_millis"),
                        rs.getLong("response_size"),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        createdAt == null ? Instant.now() : createdAt.toInstant());
            }
        };
    }
}
