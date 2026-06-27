package com.platform.quality.rule;

import java.util.List;
import java.util.Optional;

public interface QualityRuleRepository {
    QualityRuleConfig save(QualityRuleConfig config);

    Optional<QualityRuleConfig> findById(long id);

    List<QualityRuleConfig> findAll(QualityDimension dimension, Boolean enabled);

    void delete(long id);
}
