package com.platform.auth;

import com.platform.common.audit.AuditLogger;
import com.platform.common.auth.AuthPrincipal;
import com.platform.common.auth.JwtUtil;
import com.platform.common.exception.BusinessException;
import com.platform.common.security.PermissionCodes;
import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 认证服务。优先使用 JdbcTemplate 落表（t_user/t_user_permission/t_role/t_role_permission），
 * 当 JdbcTemplate 不可用（如单元测试）时回退到内存仓储。
 */
public class AuthService {
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, UserAccount> fallbackUsers = new ConcurrentHashMap<>();
    private final Map<String, Role> fallbackRoles = new ConcurrentHashMap<>();

    public AuthService(String jwtSecret, Clock clock) {
        this(new JwtUtil(jwtSecret, clock), null);
    }

    public AuthService(JwtUtil jwtUtil) {
        this(jwtUtil, null);
    }

    public AuthService(JwtUtil jwtUtil, JdbcTemplate jdbcTemplate) {
        this.jwtUtil = jwtUtil;
        this.jdbcTemplate = jdbcTemplate;
        if (jdbcTemplate == null) {
            fallbackUsers.put("admin", new UserAccount("admin",
                    PASSWORD_ENCODER.encode("admin123"), new HashSet<>(PermissionCodes.ALL)));
        }
    }

    private boolean useDb() {
        return jdbcTemplate != null;
    }

    public String login(String username, String password) {
        UserAccount account = findUser(username);
        if (account == null || !PASSWORD_ENCODER.matches(password, account.passwordHash())) {
            throw new BusinessException("AUTH-401", "bad credentials");
        }
        AuditLogger.record("login", username);
        return jwtUtil.issue(username, account.permissions(), 3600);
    }

    public String refresh(String token) {
        AuthPrincipal principal = jwtUtil.parse(token);
        UserAccount account = findUser(principal.username());
        if (account == null) {
            throw new BusinessException("AUTH-401", "user disabled");
        }
        return jwtUtil.issue(account.username(), account.permissions(), 3600);
    }

    public void requirePermission(String token, String permission) {
        AuthPrincipal principal = jwtUtil.parse(token);
        if (!principal.hasPermission(permission)) {
            throw new BusinessException("AUTH-403", "permission denied");
        }
    }

    public AuthPrincipal parse(String token) {
        return jwtUtil.parse(token);
    }

    public List<UserAccount> listUsers() {
        if (useDb()) {
            return jdbcTemplate.queryForList("SELECT id, username FROM t_user", Long.class).isEmpty()
                    ? List.of() : listUsersFromDb();
        }
        return List.copyOf(fallbackUsers.values());
    }

    private List<UserAccount> listUsersFromDb() {
        List<Long> userIds = jdbcTemplate.queryForList("SELECT id FROM t_user", Long.class);
        return userIds.stream().map(id -> loadUserFromDb(id)).filter(java.util.Objects::nonNull).toList();
    }

    public UserAccount createUser(String username, String password, Set<String> permissions) {
        if (useDb()) {
            if (findUser(username) != null) {
                throw new BusinessException("USER-409", "username already exists");
            }
            jdbcTemplate.update("INSERT INTO t_user (username, password_hash) VALUES (?, ?)",
                    username, PASSWORD_ENCODER.encode(password));
            Long userId = jdbcTemplate.queryForObject("SELECT id FROM t_user WHERE username = ?", Long.class, username);
            savePermissions(userId, permissions);
            return new UserAccount(username, null, permissions);
        }
        if (fallbackUsers.containsKey(username)) {
            throw new BusinessException("USER-409", "username already exists");
        }
        UserAccount account = new UserAccount(username, PASSWORD_ENCODER.encode(password), new HashSet<>(permissions));
        fallbackUsers.put(username, account);
        return account;
    }

    public UserAccount updateUser(String username, Set<String> permissions) {
        if (useDb()) {
            Long userId = jdbcTemplate.queryForObject("SELECT id FROM t_user WHERE username = ?", Long.class, username);
            if (userId == null) {
                throw new BusinessException("USER-404", "user not found");
            }
            jdbcTemplate.update("DELETE FROM t_user_permission WHERE user_id = ?", userId);
            savePermissions(userId, permissions);
            return new UserAccount(username, null, permissions);
        }
        UserAccount account = fallbackUsers.get(username);
        if (account == null) {
            throw new BusinessException("USER-404", "user not found");
        }
        UserAccount updated = new UserAccount(username, account.passwordHash(), new HashSet<>(permissions));
        fallbackUsers.put(username, updated);
        return updated;
    }

    public Role createRole(String name, Set<String> permissions) {
        if (useDb()) {
            try {
                jdbcTemplate.update("INSERT INTO t_role (name) VALUES (?)", name);
            } catch (Exception ex) {
                throw new BusinessException("ROLE-409", "role already exists");
            }
            Long roleId = jdbcTemplate.queryForObject("SELECT id FROM t_role WHERE name = ?", Long.class, name);
            saveRolePermissions(roleId, permissions);
            return new Role(name, permissions);
        }
        if (fallbackRoles.containsKey(name)) {
            throw new BusinessException("ROLE-409", "role already exists");
        }
        Role role = new Role(name, new HashSet<>(permissions));
        fallbackRoles.put(name, role);
        return role;
    }

    public Role updateRolePermissions(String name, Set<String> permissions) {
        if (useDb()) {
            Long roleId = jdbcTemplate.queryForObject("SELECT id FROM t_role WHERE name = ?", Long.class, name);
            if (roleId == null) {
                throw new BusinessException("ROLE-404", "role not found");
            }
            jdbcTemplate.update("DELETE FROM t_role_permission WHERE role_id = ?", roleId);
            saveRolePermissions(roleId, permissions);
            return new Role(name, permissions);
        }
        Role role = fallbackRoles.get(name);
        if (role == null) {
            throw new BusinessException("ROLE-404", "role not found");
        }
        Role updated = new Role(name, new HashSet<>(permissions));
        fallbackRoles.put(name, updated);
        return updated;
    }

    public List<Role> listRoles() {
        if (useDb()) {
            List<Long> roleIds = jdbcTemplate.queryForList("SELECT id FROM t_role", Long.class);
            return roleIds.stream().map(id -> loadRoleFromDb(id)).filter(java.util.Objects::nonNull).toList();
        }
        return List.copyOf(fallbackRoles.values());
    }

    public Map<String, Role> rolesSnapshot() {
        return new LinkedHashMap<>();
    }

    private UserAccount findUser(String username) {
        if (useDb()) {
            try {
                Long userId = jdbcTemplate.queryForObject("SELECT id FROM t_user WHERE username = ?", Long.class, username);
                return loadUserFromDb(userId);
            } catch (Exception ex) {
                return null;
            }
        }
        return fallbackUsers.get(username);
    }

    private UserAccount loadUserFromDb(Long userId) {
        try {
            String username = jdbcTemplate.queryForObject("SELECT username FROM t_user WHERE id = ?", String.class, userId);
            String passwordHash = jdbcTemplate.queryForObject("SELECT password_hash FROM t_user WHERE id = ?", String.class, userId);
            List<String> perms = jdbcTemplate.queryForList(
                    "SELECT permission_code FROM t_user_permission WHERE user_id = ?", String.class, userId);
            return new UserAccount(username, passwordHash, new HashSet<>(perms));
        } catch (Exception ex) {
            return null;
        }
    }

    private Role loadRoleFromDb(Long roleId) {
        try {
            String name = jdbcTemplate.queryForObject("SELECT name FROM t_role WHERE id = ?", String.class, roleId);
            List<String> perms = jdbcTemplate.queryForList(
                    "SELECT permission_code FROM t_role_permission WHERE role_id = ?", String.class, roleId);
            return new Role(name, new HashSet<>(perms));
        } catch (Exception ex) {
            return null;
        }
    }

    private void savePermissions(Long userId, Set<String> permissions) {
        for (String perm : permissions) {
            jdbcTemplate.update("INSERT IGNORE INTO t_user_permission (user_id, permission_code) VALUES (?, ?)", userId, perm);
        }
    }

    private void saveRolePermissions(Long roleId, Set<String> permissions) {
        for (String perm : permissions) {
            jdbcTemplate.update("INSERT IGNORE INTO t_role_permission (role_id, permission_code) VALUES (?, ?)", roleId, perm);
        }
    }
}