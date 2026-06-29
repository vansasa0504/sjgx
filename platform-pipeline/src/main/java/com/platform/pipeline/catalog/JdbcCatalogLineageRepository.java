package com.platform.pipeline.catalog;

import com.platform.common.db.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcCatalogLineageRepository implements CatalogLineageRepository {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcCatalogLineageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public void save(CatalogLineage lineage) {
        int affected = jdbcTemplate.update("""
                UPDATE t_catalog_lineage
                SET node_name = ?
                WHERE catalog_id = ? AND node_type = ? AND node_id = ? AND direction = ?
                """, lineage.nodeName(), lineage.catalogId(), lineage.nodeType(), lineage.nodeId(), lineage.direction());
        if (affected == 0) {
            jdbcTemplate.update("""
                    INSERT INTO t_catalog_lineage
                    (id, catalog_id, node_type, node_id, node_name, direction, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, idGenerator.nextId("t_catalog_lineage"), lineage.catalogId(), lineage.nodeType(),
                    lineage.nodeId(), lineage.nodeName(), lineage.direction());
        }
    }

    @Override
    public List<CatalogLineage> findByCatalogId(long catalogId) {
        return jdbcTemplate.query("""
                SELECT catalog_id, node_type, node_id, node_name, direction
                FROM t_catalog_lineage
                WHERE catalog_id = ?
                ORDER BY direction, node_type, node_id
                """, (rs, rowNum) -> map(rs), catalogId);
    }

    private CatalogLineage map(ResultSet rs) throws SQLException {
        return new CatalogLineage(rs.getLong("catalog_id"), rs.getString("node_type"),
                rs.getLong("node_id"), rs.getString("node_name"), rs.getString("direction"));
    }
}
