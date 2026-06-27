package com.platform.quality;

import com.platform.quality.executor.QualityCheckExecutor;
import com.platform.quality.issue.QualityIssueService;
import com.platform.quality.rule.InMemoryQualityRuleRepository;
import com.platform.quality.rule.QualityRuleRepository;
import com.platform.quality.scoring.QualityScoringService;
import com.platform.quality.scoring.QualityWeightRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {"com.platform.quality", "com.platform.common"})
public class QualityApplication {
    public static void main(String[] args) {
        SpringApplication.run(QualityApplication.class, args);
    }

    @Bean
    QualityRuleRepository qualityRuleRepository() {
        return new InMemoryQualityRuleRepository();
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
}
