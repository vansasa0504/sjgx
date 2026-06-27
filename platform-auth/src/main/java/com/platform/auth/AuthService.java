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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class AuthService {
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final JwtUtil jwtUtil;
    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();
    private final Map<String, Role> roles = new ConcurrentHashMap<>();

    public AuthService(String jwtSecret, Clock clock) {
        this(new JwtUtil(jwtSecret, clock));
    }

    public AuthService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
        users.put("admin", new UserAccount("admin", PASSWORD_ENCODER.encode("admin123"), new HashSet<>(PermissionCodes.ALL)));
    }

    public String login(String username, String password) {
        UserAccount account = users.get(username);
        if (account == null || !PASSWORD_ENCODER.matches(password, account.passwordHash())) {
            throw new BusinessException("AUTH-401", "bad credentials");
        }
        AuditLogger.record("login", username);
        return jwtUtil.issue(username, account.permissions(), 3600);
    }

    public String refresh(String token) {
        AuthPrincipal principal = jwtUtil.parse(token);
        UserAccount account = users.get(principal.username());
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
        return List.copyOf(users.values());
    }

    public UserAccount createUser(String username, String password, Set<String> permissions) {
        if (users.containsKey(username)) {
            throw new BusinessException("USER-409", "username already exists");
        }
        UserAccount account = new UserAccount(username, PASSWORD_ENCODER.encode(password), new HashSet<>(permissions));
        users.put(username, account);
        return account;
    }

    public UserAccount updateUser(String username, Set<String> permissions) {
        UserAccount account = users.get(username);
        if (account == null) {
            throw new BusinessException("USER-404", "user not found");
        }
        UserAccount updated = new UserAccount(username, account.passwordHash(), new HashSet<>(permissions));
        users.put(username, updated);
        return updated;
    }

    public Role createRole(String name, Set<String> permissions) {
        if (roles.containsKey(name)) {
            throw new BusinessException("ROLE-409", "role already exists");
        }
        Role role = new Role(name, new HashSet<>(permissions));
        roles.put(name, role);
        return role;
    }

    public Role updateRolePermissions(String name, Set<String> permissions) {
        Role role = roles.get(name);
        if (role == null) {
            throw new BusinessException("ROLE-404", "role not found");
        }
        Role updated = new Role(name, new HashSet<>(permissions));
        roles.put(name, updated);
        return updated;
    }

    public List<Role> listRoles() {
        return List.copyOf(roles.values());
    }

    public Map<String, Role> rolesSnapshot() {
        return new LinkedHashMap<>(roles);
    }
}
