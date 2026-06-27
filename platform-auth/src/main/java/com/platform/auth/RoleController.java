package com.platform.auth;

import com.platform.common.model.Result;
import com.platform.common.security.RequirePermission;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/roles")
public class RoleController {
    private final AuthService authService;

    public RoleController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    @RequirePermission("system:view")
    public Result<List<Role>> list() {
        return Result.ok(authService.listRoles());
    }

    @PostMapping
    @RequirePermission("system:create")
    public Result<Role> create(@RequestBody CreateRoleRequest request) {
        Set<String> permissions = request.permissions() == null ? Set.of() : Set.copyOf(request.permissions());
        return Result.ok(authService.createRole(request.name(), permissions));
    }

    @PutMapping("/{name}/permissions")
    @RequirePermission("system:update")
    public Result<Role> updatePermissions(@PathVariable String name, @RequestBody UpdatePermissionsRequest request) {
        Set<String> permissions = request.permissions() == null ? Set.of() : Set.copyOf(request.permissions());
        return Result.ok(authService.updateRolePermissions(name, permissions));
    }

    public record CreateRoleRequest(String name, List<String> permissions) {
    }

    public record UpdatePermissionsRequest(List<String> permissions) {
    }
}
