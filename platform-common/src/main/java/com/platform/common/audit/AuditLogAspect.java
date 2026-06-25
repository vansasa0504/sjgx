package com.platform.common.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
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
        try {
            Object result = joinPoint.proceed();
            append(auditLog, AuditStatus.SUCCESS, null);
            return result;
        } catch (Throwable ex) {
            append(auditLog, AuditStatus.FAILED, ex.getClass().getSimpleName() + ":" + ex.getMessage());
            throw ex;
        }
    }

    private void append(AuditLog auditLog, AuditStatus status, String error) {
        String detail = error == null ? "method audit" : error;
        AuditEvent event = AuditEvent.system(auditLog.eventType(), auditLog.value(), detail, status);
        AuditLogger.record(auditLog.value(), event.actorId());
        AuditLogRepository repository = repositoryProvider.getIfAvailable();
        if (repository != null) {
            repository.append(event);
        }
    }
}