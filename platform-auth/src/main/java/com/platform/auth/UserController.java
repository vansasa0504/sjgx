package com.platform.auth;

import com.platform.common.model.Page;
import com.platform.common.model.Result;
import com.platform.common.security.PermissionCodes;
import com.platform.common.security.RequirePermission;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {
    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    @RequirePermission("system:view")
    public Result<Page<UserResponse>> list(@RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        List<UserResponse> users = authService.listUsers().stream()
                .filter(u -> keyword == null || keyword.isBlank() || u.username().contains(keyword))
                .map(UserResponse::from)
                .toList();
        int safeSize = size <= 0 ? 10 : size;
        int safePage = page <= 0 ? 1 : page;
        int from = Math.min((safePage - 1) * safeSize, users.size());
        int to = Math.min(from + safeSize, users.size());
        return Result.ok(Page.of(users.subList(from, to), users.size(), safePage, safeSize));
    }

    @PostMapping
    @RequirePermission("system:create")
    public Result<UserResponse> create(@RequestBody CreateUserRequest request) {
        Set<String> permissions = request.permissions() == null ? Set.of() : Set.copyOf(request.permissions());
        return Result.ok(UserResponse.from(authService.createUser(request.username(), request.password(), permissions)));
    }

    @PutMapping("/{username}")
    @RequirePermission("system:update")
    public Result<UserResponse> update(@PathVariable String username, @RequestBody UpdateUserRequest request) {
        Set<String> permissions = request.permissions() == null ? Set.of() : Set.copyOf(request.permissions());
        return Result.ok(UserResponse.from(authService.updateUser(username, permissions)));
    }

    public record UserResponse(String username, Set<String> permissions) {
        static UserResponse from(UserAccount account) {
            return new UserResponse(account.username(), account.permissions());
        }
    }

    public record CreateUserRequest(String username, String password, List<String> permissions) {
    }

    public record UpdateUserRequest(List<String> permissions) {
    }
}
