package com.platform.quality.rule;

public record QualityViolation(String ruleCode,
                               QualityDimension dimension,
                               int rowIndex,
                               String field,
                               String message) {
}
