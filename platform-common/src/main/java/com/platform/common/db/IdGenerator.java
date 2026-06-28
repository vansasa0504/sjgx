package com.platform.common.db;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 并发安全的数据库 ID 生成器。
 *
 * <p>替代 {@code SELECT MAX(id)+1} 模式：初始化时从各表 MAX(id) 读取当前最大值，
 * 后续在内存中以 {@link AtomicLong} 原子递增，避免并发插入主键冲突。
 * 单实例内线程安全；多实例部署需结合雪花算法或数据库序列（见 RW-1 备注）。
 */
public final class IdGenerator {
    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public IdGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 返回指定表的下一个 ID。首次调用时从 MAX(id) 初始化计数器。
     *
     * @param table 目标表名（不可拼接外部输入，防止注入）
     * @return 下一个主键值
     */
    public long nextId(String table) {
        AtomicLong counter = counters.computeIfAbsent(table, this::loadCounter);
        return counter.incrementAndGet();
    }

    private AtomicLong loadCounter(String table) {
        try {
            Long max = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
            return new AtomicLong(max == null ? 0L : max);
        } catch (Exception ex) {
            // 表可能尚不存在（如轻量测试场景），从 0 开始
            return new AtomicLong(0L);
        }
    }
}
