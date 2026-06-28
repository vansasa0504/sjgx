package com.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.common.auth.JwtUtil;
import com.platform.common.exception.BusinessException;
import com.platform.common.security.PermissionCodes;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AuthService JDBC-path integration test: verifies that user/role/permission
 * CRUD works through JdbcTemplate against H2 (MySQL mode) with Flyway migrations.
 */
class AuthJdbcRepositoryTest {
    private JdbcTemplate jdbc;
    private AuthService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:auth_jdbc;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("filesystem:../db/migration").load().migrate();

        // bootstrap requires env var
        System.setProperty("AUTH_BOOTSTRAP_ADMIN_PASSWORD", "test-admin-pw");
        service = new AuthService(new JwtUtil("test-secret", Clock.systemUTC()), jdbc);
    }

    @Test
    void bootstrapAdminCreatesUserAndPermissions() {
        // admin user should exist in DB
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM t_user WHERE username = 'admin'", Long.class);
        assertEquals(1L, count);

        // all permission codes should be seeded
        Long permCount = jdbc.queryForObject("SELECT COUNT(*) FROM t_permission", Long.class);
        assertEquals((long) PermissionCodes.ALL.size(), permCount);

        // admin should have all permissions in t_user_permission
        Long adminPermCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user_permission WHERE user_id = (SELECT id FROM t_user WHERE username = 'admin')",
                Long.class);
        assertEquals((long) PermissionCodes.ALL.size(), adminPermCount);
    }

    @Test
    void loginWithBootstrapPasswordSucceeds() {
        String token = service.login("admin", "test-admin-pw");
        assertNotNull(token);
        assertEquals("admin", service.parse(token).username());
    }

    @Test
    void createUserPersistsToDb() {
        Set<String> perms = new HashSet<>();
        perms.add("partner:view");
        perms.add("partner:create");

        service.createUser("testuser", "password123", perms);

        // verify in DB
        Long userId = jdbc.queryForObject("SELECT id FROM t_user WHERE username = 'testuser'", Long.class);
        assertNotNull(userId);

        Long permCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user_permission WHERE user_id = ?", Long.class, userId);
        assertEquals(2L, permCount);
    }

    @Test
    void updateUserReplacesPermissions() {
        Set<String> perms1 = new HashSet<>();
        perms1.add("partner:view");
        service.createUser("permuser", "pw", perms1);

        Set<String> perms2 = new HashSet<>();
        perms2.add("partner:view");
        perms2.add("consumer:view");
        perms2.add("billing:view");
        service.updateUser("permuser", perms2);

        Long userId = jdbc.queryForObject("SELECT id FROM t_user WHERE username = 'permuser'", Long.class);
        Long permCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user_permission WHERE user_id = ?", Long.class, userId);
        assertEquals(3L, permCount);
    }

    @Test
    void createRolePersistsToDb() {
        Set<String> perms = new HashSet<>();
        perms.add("quality:view");
        perms.add("quality:run");

        service.createRole("auditor", perms);

        Long roleId = jdbc.queryForObject("SELECT id FROM t_role WHERE name = 'auditor'", Long.class);
        assertNotNull(roleId);

        Long permCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_role_permission WHERE role_id = ?", Long.class, roleId);
        assertEquals(2L, permCount);
    }

    @Test
    void duplicateUsernameThrows() {
        service.createUser("dup", "pw", Set.of("partner:view"));
        assertThrows(BusinessException.class, () -> service.createUser("dup", "pw", Set.of("partner:view")));
    }

    @Test
    void listUsersReturnsAllFromDb() {
        service.createUser("user1", "pw", Set.of("partner:view"));
        service.createUser("user2", "pw", Set.of("partner:view"));
        // admin + 2 new = 3
        assertTrue(service.listUsers().size() >= 3);
    }

    @Test
    void restartRecoveryDataPersistsAcrossNewServiceInstance() {
        // create user with first service instance
        service.createUser("persistuser", "mypw", Set.of("partner:view", "billing:view"));

        // simulate restart: create new AuthService pointing at same DB
        AuthService recovered = new AuthService(new JwtUtil("test-secret", Clock.systemUTC()), jdbc);

        // user should still exist and be loginable
        String token = recovered.login("persistuser", "mypw");
        assertNotNull(token);
        assertEquals("persistuser", recovered.parse(token).username());
        assertTrue(recovered.parse(token).hasPermission("partner:view"));
        assertTrue(recovered.parse(token).hasPermission("billing:view"));
    }
}