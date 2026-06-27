package com.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.common.security.PermissionCodes;
import org.junit.jupiter.api.Test;

class PermissionControllerTest {
    @Test
    void listsAllPermissionCodes() {
        PermissionController controller = new PermissionController();
        assertEquals(PermissionCodes.ALL, controller.list().data());
        assertTrue(controller.list().data().contains("partner:view"));
    }
}
