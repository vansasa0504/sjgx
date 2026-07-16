package com.platform.auth;

import com.platform.common.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AdvancedAuthController {
    private final AdvancedAuthService service;

    public AdvancedAuthController(AdvancedAuthService service) {
        this.service = service;
    }

    @PostMapping("/login/mfa")
    public Result<AuthController.TokenResponse> mfa(@RequestBody MfaLoginRequest request) {
        return Result.ok(new AuthController.TokenResponse(
                service.completeChallenge(request.challengeId(), request.code())));
    }

    @PostMapping("/mfa/bind")
    public Result<MfaSecret> bind(@RequestHeader("Authorization") String authorization) {
        return Result.ok(new MfaSecret(service.beginMfaBinding(authorization)));
    }

    @PostMapping("/mfa/confirm")
    public Result<Void> confirm(@RequestHeader("Authorization") String authorization,
            @RequestBody TotpRequest request) {
        service.confirmMfaBinding(authorization, request.code());
        return Result.ok(null);
    }

    @PostMapping("/mfa/unbind")
    public Result<Void> unbind(@RequestHeader("Authorization") String authorization,
            @RequestBody TotpRequest request) {
        service.unbindMfa(authorization, request.code());
        return Result.ok(null);
    }

    @GetMapping("/login/cert")
    public Result<AdvancedAuthService.CertificateChallengeResponse> certChallenge(
            @RequestParam String fingerprint) {
        return Result.ok(service.certificateChallenge(fingerprint));
    }

    @PostMapping("/login/cert")
    public Result<AuthController.TokenResponse> certLogin(@RequestBody CertificateLoginRequest request) {
        return Result.ok(new AuthController.TokenResponse(service.certificateLogin(
                request.pem(), request.challengeId(), request.signature())));
    }

    @PostMapping("/cert/bind")
    public Result<CertificateFingerprint> bindCert(@RequestHeader("Authorization") String authorization,
            @RequestBody CertificateRequest request) {
        return Result.ok(new CertificateFingerprint(service.bindCertificate(authorization, request.pem())));
    }

    @PostMapping("/cert/revoke")
    public Result<Void> revokeCert(@RequestHeader("Authorization") String authorization,
            @RequestBody CertificateFingerprint request) {
        service.revokeCertificate(authorization, request.fingerprint());
        return Result.ok(null);
    }

    @PostMapping("/cert/rotate")
    public Result<CertificateFingerprint> rotateCert(@RequestHeader("Authorization") String authorization,
            @RequestBody RotateCertificateRequest request) {
        return Result.ok(new CertificateFingerprint(
                service.rotateCertificate(authorization, request.oldFingerprint(), request.pem())));
    }

    @GetMapping("/sso/redirect")
    public Result<AdvancedAuthService.SsoRedirect> ssoRedirect() {
        return Result.ok(service.ssoRedirect());
    }

    @GetMapping("/sso/callback")
    public Result<AuthController.TokenResponse> ssoCallback(@RequestParam String code,
            @RequestParam String state) {
        return Result.ok(new AuthController.TokenResponse(service.ssoCallback(code, state)));
    }

    public record MfaLoginRequest(String challengeId, String code) {}
    public record MfaSecret(String secret) {}
    public record TotpRequest(String code) {}
    public record CertificateRequest(String pem) {}
    public record CertificateLoginRequest(String pem, String challengeId, String signature) {}
    public record CertificateFingerprint(String fingerprint) {}
    public record RotateCertificateRequest(String oldFingerprint, String pem) {}
}