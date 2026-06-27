package com.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserControllerTest {
    @Test
    void createsListsAndUpdatesUser() {
        AuthService authService = new AuthService("test-secret", Clock.systemUTC());
        UserController controller = new UserController(authService);

        controller.create(new UserController.CreateUserRequest("alice", "pw", List.of("partner:view")));
        assertTrue(controller.list(null, 1, 10).data().records().stream().anyMatch(u -> "alice".equals(u.username())));

        controller.update("alice", new UserController.UpdateUserRequest(List.of("partner:view", "partner:create")));
        assertEquals(2, authService.listUsers().stream().filter(u -> "alice".equals(u.username())).findFirst().orElseThrow().permissions().size());

        assertThrows(Exception.class, () -> controller.create(new UserController.CreateUserRequest("alice", "pw", List.of())));
    }
}
