package com.platform.pipeline.ingest.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.pipeline.ingest.ProtocolAdapter;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DbAdapter implements ProtocolAdapter {
    private final Map<String, DataSource> dataSources;
    private final ObjectMapper objectMapper;

    public DbAdapter(Map<String, DataSource> dataSources) {
        this(dataSources, new ObjectMapper());
    }

    DbAdapter(Map<String, DataSource> dataSources, ObjectMapper objectMapper) {
        this.dataSources = dataSources;
        this.objectMapper = objectMapper;
    }

    @Override
    public String protocol() { return "DB"; }

    @Override
    public String fetch(URI endpoint) {
        try {
            String sourceKey = endpoint.getHost();
            String sql = URLDecoder.decode(endpoint.getPath().substring(1), StandardCharsets.UTF_8);
            DataSource dataSource = dataSources.get(sourceKey);
            if (dataSource == null) {
                throw new IllegalArgumentException("unknown datasource: " + sourceKey);
            }
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return objectMapper.writeValueAsString(rows);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("DB fetch failed", ex);
        }
    }
}
