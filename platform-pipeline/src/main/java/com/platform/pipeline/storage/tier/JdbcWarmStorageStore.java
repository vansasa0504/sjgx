package com.platform.pipeline.storage.tier;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class JdbcWarmStorageStore implements WarmStorageStore {
    private final DataSource dataSource;

    public JdbcWarmStorageStore(DataSource dataSource) {
        this.dataSource = dataSource;
        init();
    }

    @Override
    public void write(TieredRecord record) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO t_warm_storage(storage_key, payload, storage_tier) VALUES(?, ?, ?)")) {
            statement.setString(1, record.key());
            statement.setString(2, record.payload());
            statement.setString(3, record.tier().name());
            statement.executeUpdate();
        } catch (Exception ex) {
            throw new IllegalStateException("warm storage write failed", ex);
        }
    }

    @Override
    public List<TieredRecord> readAll() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT storage_key, payload, storage_tier FROM t_warm_storage")) {
            List<TieredRecord> records = new ArrayList<>();
            while (rs.next()) {
                records.add(new TieredRecord(rs.getString(1), rs.getString(2), StorageTier.valueOf(rs.getString(3))));
            }
            return records;
        } catch (Exception ex) {
            throw new IllegalStateException("warm storage read failed", ex);
        }
    }

    private void init() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS t_warm_storage(id BIGINT AUTO_INCREMENT PRIMARY KEY, storage_key VARCHAR(128), payload VARCHAR(4000), storage_tier VARCHAR(16))");
        } catch (Exception ex) {
            throw new IllegalStateException("warm storage init failed", ex);
        }
    }
}
