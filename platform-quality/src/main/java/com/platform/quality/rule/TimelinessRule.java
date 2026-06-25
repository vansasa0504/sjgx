package com.platform.quality.rule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class TimelinessRule extends AbstractQualityRule {
    private final Clock clock;

    public TimelinessRule() {
        this(Clock.systemUTC());
    }

    public TimelinessRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public QualityDimension dimension() {
        return QualityDimension.TIMELINESS;
    }

    @Override
    public List<QualityViolation> evaluate(List<Map<String, Object>> rows, QualityRuleConfig config) {
        long maxAgeMinutes = longExpression(config, "maxAgeMinutes", 60);
        return oneByOne(rows, config, row -> {
            try {
                Instant actual = instant(value(row, config.field()));
                long age = Duration.between(actual, clock.instant()).toMinutes();
                return age > maxAgeMinutes ? "record is older than allowed window" : null;
            } catch (Exception ex) {
                return "timestamp is invalid";
            }
        });
    }
}
