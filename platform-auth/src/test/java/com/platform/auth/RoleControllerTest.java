package com.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleControllerTest {
    @Test
    void createsListsAndUpdatesRole() {
        AuthService authService = new AuthService("test-secret", Clock.systemUTC());
        RoleController controller = new RoleController(authService);

        controller.create(new RoleController.CreateRoleRequest("risk-admin", List.of("partner:view", "quality:view")));
        assertTrue(authService.listRoles().stream().anyMatch(r -> "risk-admin".equals(r.name())));

        controller.updatePermissions("risk-admin", new RoleController.UpdatePermissionsRequest(List.of("partner:view")));
        assertEquals(1, authService.listRoles().stream().filter(r -> "risk-admin".equals(r.name())).findFirst().orElseThrow().permissions().size());
    }
}
