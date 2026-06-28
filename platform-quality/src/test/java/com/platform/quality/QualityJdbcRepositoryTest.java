package com.platform.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.quality.rule.JdbcQualityRuleRepository;
import com.platform.quality.rule.QualityRuleConfig;
import com.platform.quality.rule.QualityRuleRepository;
import com.platform.quality.rule.QualityDimension;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class QualityJdbcRepositoryTest {

    @Test
    void saveAndFindRuleViaJdbc() {
        JdbcTemplate jdbc = migrate("quality_jdbc");
        QualityRuleRepository repo = new JdbcQualityRuleRepository(jdbc);

        QualityRuleConfig rule = new QualityRuleConfig(null, "QR-01", QualityDimension.COMPLETENESS,
                "amount", Map.of("weight", 80), 80);
        QualityRuleConfig saved = repo.save(rule);
        assertEquals("QR-01", saved.ruleCode());

        QualityRuleConfig found = repo.findById(saved.id()).orElseThrow();
        assertEquals("QR-01", found.ruleCode());
        assertEquals(QualityDimension.COMPLETENESS, found.dimension());
    }

    @Test
    void findAllFiltersByDimension() {
        JdbcTemplate jdbc = migrate("quality_filter");
        QualityRuleRepository repo = new JdbcQualityRuleRepository(jdbc);

        repo.save(new QualityRuleConfig(null, "QR-A", QualityDimension.COMPLETENESS, "f1", Map.of("weight", 50), 50));
        repo.save(new QualityRuleConfig(null, "QR-B", QualityDimension.TIMELINESS, "f2", Map.of("weight", 60), 60));

        List<QualityRuleConfig> all = repo.findAll(null, null);
        assertEquals(2, all.size());

        List<QualityRuleConfig> filtered = repo.findAll(QualityDimension.COMPLETENESS, null);
        assertEquals(1, filtered.size());
        assertEquals("QR-A", filtered.get(0).ruleCode());
    }

    @Test
    void deleteRemovesRule() {
        JdbcTemplate jdbc = migrate("quality_delete");
        QualityRuleRepository repo = new JdbcQualityRuleRepository(jdbc);

        QualityRuleConfig saved = repo.save(new QualityRuleConfig(null, "QR-DEL",
                QualityDimension.COMPLETENESS, "f", Map.of("weight", 40), 40));
        repo.delete(saved.id());

        assertTrue(repo.findById(saved.id()).isEmpty());
    }

    private JdbcTemplate migrate(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("filesystem:../db/migration").load().migrate();
        return jdbc;
    }
}