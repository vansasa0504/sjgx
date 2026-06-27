package com.platform.common.security;

import java.util.List;

/**
 * 平台全量权限码。供 admin 种子用户、{@code /permissions} 端点与前端菜单/按钮控制使用。
 */
public final class PermissionCodes {
    public static final List<String> ALL = List.of(
            "partner:view", "partner:create", "partner:update", "partner:approve",
            "consumer:view", "consumer:create", "consumer:update", "consumer:approve",
            "ingest:view", "ingest:create", "ingest:update", "ingest:approve",
            "service:view", "service:create", "service:update", "service:approve",
            "catalog:view", "catalog:apply", "catalog:approve",
            "quality:view", "quality:create", "quality:update", "quality:run",
            "billing:view", "billing:create", "billing:update", "billing:approve", "billing:run",
            "stats:view",
            "system:view", "system:create", "system:update"
    );

    private PermissionCodes() {
    }
}
