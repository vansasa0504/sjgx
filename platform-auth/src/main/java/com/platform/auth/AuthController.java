package com.platform.auth;

import com.platform.common.auth.AuthPrincipal;
import com.platform.common.model.Result;
import com.platform.common.security.PermissionCodes;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<TokenResponse> login(@RequestBody LoginRequest request) {
        return Result.ok(new TokenResponse(authService.login(request.username(), request.password())));
    }

    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@RequestHeader("Authorization") String authorization) {
        return Result.ok(new TokenResponse(authService.refresh(bearer(authorization))));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok(null);
    }

    @GetMapping("/permissions")
    public Result<List<String>> permissions(@RequestHeader("Authorization") String authorization) {
        AuthPrincipal principal = authService.parse(bearer(authorization));
        return Result.ok(List.copyOf(principal.permissions()));
    }

    @GetMapping("/all-permissions")
    public Result<List<String>> allPermissions() {
        return Result.ok(PermissionCodes.ALL);
    }

    private String bearer(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : authorization;
    }

    public record LoginRequest(String username, String password) {
    }

    public record TokenResponse(String token) {
    }
}
