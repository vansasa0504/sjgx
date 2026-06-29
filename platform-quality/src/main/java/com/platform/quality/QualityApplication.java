package com.platform.quality;

import com.platform.quality.executor.QualityCheckExecutor;
import com.platform.quality.issue.QualityIssueService;
import com.platform.quality.report.InMemoryQualityReportRepository;
import com.platform.quality.report.JdbcQualityReportRepository;
import com.platform.quality.report.QualityReportRepository;
import com.platform.quality.report.QualityReportService;
import com.platform.quality.rule.InMemoryQualityRuleRepository;
import com.platform.quality.rule.JdbcQualityRuleRepository;
import com.platform.quality.rule.QualityRuleRepository;
import com.platform.quality.scoring.QualityScoringService;
import com.platform.quality.scoring.QualityWeightRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication(scanBasePackages = {"com.platform.quality", "com.platform.common"})
public class QualityApplication {
    public static void main(String[] args) {
        SpringApplication.run(QualityApplication.class, args);
    }

    @Bean
    QualityRuleRepository qualityRuleRepository(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate != null
                ? new JdbcQualityRuleRepository(jdbcTemplate)
                : new InMemoryQualityRuleRepository();
    }

    @Bean
    QualityCheckExecutor qualityCheckExecutor() {
        return new QualityCheckExecutor();
    }

    @Bean
    QualityIssueService qualityIssueService() {
        return new QualityIssueService();
    }

    @Bean
    QualityWeightRepository qualityWeightRepository() {
        return QualityWeightRepository.empty();
    }

    @Bean
    QualityScoringService qualityScoringService(QualityWeightRepository weightRepository) {
        return new QualityScoringService(weightRepository);
    }

    @Bean
    QualityReportRepository qualityReportRepository(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate != null
                ? new JdbcQualityReportRepository(jdbcTemplate)
                : new InMemoryQualityReportRepository();
    }

    @Bean
    QualityReportService qualityReportService(QualityCheckExecutor checkExecutor,
                                              QualityReportRepository reportRepository) {
        return new QualityReportService(checkExecutor, reportRepository);
    }
}
