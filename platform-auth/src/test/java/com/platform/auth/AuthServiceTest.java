package com.platform.auth;

import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {
    @Test
    void loginSucceedsAndRefreshesToken() {
        AuthService service = new AuthService("secret", Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneOffset.UTC));

        String token = service.login("admin", "admin123");
        String refreshed = service.refresh(token);

        assertEquals("admin", service.parse(token).username());
        assertEquals("admin", service.parse(refreshed).username());
    }

    @Test
    void controllerExposesLoginRefreshAndLogout() {
        AuthService service = new AuthService("secret", Clock.systemUTC());
        AuthController controller = new AuthController(service);

        String token = controller.login(new AuthController.LoginRequest("admin", "admin123")).data().token();

        assertNotNull(token);
        assertNotNull(controller.refresh("Bearer " + token).data().token());
        assertEquals(true, controller.logout().success());
    }

    @Test
    void loginFailsForBadPassword() {
        AuthService service = new AuthService("secret", Clock.systemUTC());

        assertThrows(BusinessException.class, () -> service.login("admin", "wrong"));
    }

    @Test
    void rejectsExpiredToken() {
        com.platform.common.auth.JwtUtil issuer = new com.platform.common.auth.JwtUtil("secret", Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneOffset.UTC));
        String token = issuer.issue("admin", java.util.Set.of(), 1);
        AuthService verifier = new AuthService("secret", Clock.fixed(Instant.parse("2026-06-25T00:00:02Z"), ZoneOffset.UTC));

        assertThrows(BusinessException.class, () -> verifier.parse(token));
    }

    @Test
    void rejectsMissingPermission() {
        AuthService service = new AuthService("secret", Clock.systemUTC());
        String token = service.login("admin", "admin123");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requirePermission(token, "system:delete"));
        assertEquals("AUTH-403", ex.code());
    }
}
