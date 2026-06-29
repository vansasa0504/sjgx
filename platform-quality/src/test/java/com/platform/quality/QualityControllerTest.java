package com.platform.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.quality.executor.QualityCheckExecutor;
import com.platform.quality.issue.QualityIssueService;
import com.platform.quality.rule.InMemoryQualityRuleRepository;
import com.platform.quality.rule.QualityDimension;
import com.platform.quality.rule.QualityRuleRepository;
import com.platform.quality.report.InMemoryQualityReportRepository;
import com.platform.quality.report.QualityReportService;
import com.platform.quality.scoring.QualityScoringService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QualityControllerTest {
    @Test
    void createsAndListsRule() {
        QualityRuleRepository repository = new InMemoryQualityRuleRepository();
        QualityController controller = new QualityController(repository, new QualityCheckExecutor(),
                new QualityIssueService(), new QualityScoringService(), new QualityReportService(new QualityCheckExecutor(), new InMemoryQualityReportRepository()));

        var rule = controller.createRule(new QualityController.CreateRuleRequest(
                "name-required", QualityDimension.COMPLETENESS, "name", Map.of(), 100)).data();
        assertEquals("name-required", rule.ruleCode());
        assertTrue(controller.listRules(null, null).data().stream().anyMatch(r -> "name-required".equals(r.ruleCode())));
    }
}
