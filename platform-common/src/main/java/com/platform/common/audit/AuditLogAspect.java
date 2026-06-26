package com.platform.common.audit;

import com.platform.common.auth.AuthPrincipal;
import com.platform.common.security.DesensitizeUtil;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditLogAspect {
    private final ObjectProvider<AuditLogRepository> repositoryProvider;

    public AuditLogAspect(ObjectProvider<AuditLogRepository> repositoryProvider) {
        this.repositoryProvider = repositoryProvider;
    }

    @Around("@annotation(auditLog)")
    public Object record(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        String argsSummary = summarizeArgs(joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            append(auditLog, AuditStatus.SUCCESS, argsSummary, null);
            return result;
        } catch (Throwable ex) {
            append(auditLog, AuditStatus.FAILED, argsSummary, ex.getClass().getSimpleName() + ":" + ex.getMessage());
            throw ex;
        }
    }

    private void append(AuditLog auditLog, AuditStatus status, String argsSummary, String error) {
        Actor actor = currentActor();
        String detail = error == null ? argsSummary : argsSummary + "; error=" + sanitize(error);
        AuditEvent event = new AuditEvent(null, UUID.randomUUID().toString(), auditLog.eventType(), actor.actorType(),
                actor.actorId(), auditLog.value(), auditLog.value(), auditLog.value(), detail, "", "", status, Instant.now());
        AuditLogger.record(auditLog.value(), event.actorId());
        AuditLogRepository repository = repositoryProvider.getIfAvailable();
        if (repository != null) {
            repository.append(event);
        }
    }

    private Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new Actor("SYSTEM", "system");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthPrincipal authPrincipal) {
            return new Actor("USER", authPrincipal.username());
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return new Actor("SYSTEM", "system");
        }
        return new Actor("USER", name);
    }

    private String summarizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "args=[]";
        }
        String summary = Arrays.stream(args)
                .map(arg -> arg == null ? "null" : sanitize(String.valueOf(arg)))
                .limit(8)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return "args=[" + summary + (args.length > 8 ? ",..." : "") + "]";
    }

    private String sanitize(String value) {
        String sanitized = value;
        sanitized = sanitized.replaceAll("(?i)(password|secret|token|credential|apiKey)=([^,;\\s}]+)", "$1=****");
        sanitized = sanitized.replaceAll("(?i)(password|secret|token|credential|apiKey):([^,;\\s}]+)", "$1:****");
        sanitized = replaceMatches(sanitized, Pattern.compile("\\b1[3-9]\\d{9}\\b"), 3, 4, false);
        sanitized = replaceMatches(sanitized, Pattern.compile("\\b\\d{17}[0-9Xx]\\b"), 6, 4, true);
        return sanitized.length() > 512 ? sanitized.substring(0, 512) + "..." : sanitized;
    }

    private String replaceMatches(String value, Pattern pattern, int prefix, int suffix, boolean upperCase) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String matched = upperCase ? matcher.group().toUpperCase(Locale.ROOT) : matcher.group();
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(DesensitizeUtil.mask(matched, prefix, suffix)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private record Actor(String actorType, String actorId) {
    }
}
