package com.platform.common.model;

import java.util.List;

/**
 * 通用分页结果。与内存仓储/未来 MyBatis-Plus 分页对齐。
 */
public record Page<T>(List<T> records, long total, long page, long size) {
    public static <T> Page<T> of(List<T> records, long total, long page, long size) {
        return new Page<>(records, total, page, size);
    }
}
