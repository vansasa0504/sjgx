package com.platform.quality.rule;

import com.platform.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class InMemoryQualityRuleRepository implements QualityRuleRepository {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, QualityRuleConfig> rules = new ConcurrentHashMap<>();

    @Override
    public QualityRuleConfig save(QualityRuleConfig config) {
        long id = config.id() == null ? ids.getAndIncrement() : config.id();
        QualityRuleConfig saved = new QualityRuleConfig(id, config.ruleCode(), config.dimension(),
                config.field(), config.expression(), config.weight());
        rules.put(id, saved);
        return saved;
    }

    @Override
    public Optional<QualityRuleConfig> findById(long id) {
        return Optional.ofNullable(rules.get(id));
    }

    @Override
    public List<QualityRuleConfig> findAll(QualityDimension dimension, Boolean enabled) {
        Stream<QualityRuleConfig> stream = rules.values().stream();
        if (dimension != null) {
            stream = stream.filter(r -> dimension.equals(r.dimension()));
        }
        return stream.toList();
    }

    @Override
    public void delete(long id) {
        if (rules.remove(id) == null) {
            throw new BusinessException("QUALITY-404", "quality rule not found");
        }
    }
}
