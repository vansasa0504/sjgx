package com.platform.auth;

import com.platform.common.model.Result;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/** Replaces a first-factor token with a short-lived MFA challenge for enrolled users. */
@Aspect
@Component
public class MfaLoginEnforcementAspect {
    private final AdvancedAuthService advanced;
    private final AuthService authService;

    public MfaLoginEnforcementAspect(AdvancedAuthService advanced, AuthService authService) {
        this.advanced = advanced;
        this.authService = authService;
    }

    @Around("execution(* com.platform.auth.AuthController.login(..)) && args(request)")
    public Object enforce(ProceedingJoinPoint joinPoint, AuthController.LoginRequest request) throws Throwable {
        if (!advanced.mfaEnabled(request.username())) return joinPoint.proceed();
        authService.authenticate(request.username(), request.password());
        String challenge = advanced.startChallenge(request.username());
        return new Result<>(true, "AUTH-MFA-REQUIRED", "MFA required",
                new AuthController.TokenResponse(challenge));
    }
}
