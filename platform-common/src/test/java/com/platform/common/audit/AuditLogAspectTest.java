package com.platform.common.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.SourceLocation;
import org.aspectj.runtime.internal.AroundClosure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditLogAspectTest {
    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsActorAndSanitizedArgumentDetail() throws Throwable {
        InMemoryAuditLogRepository repository = new InMemoryAuditLogRepository();
        AuditLogAspect aspect = new AuditLogAspect(provider(repository));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice", "n/a", List.of()));
        Method method = AuditedService.class.getDeclaredMethod("operate", String.class);
        AuditLog auditLog = method.getAnnotation(AuditLog.class);

        aspect.record(new SimpleJoinPoint(new Object[] {"phone=13812345678,secret=plain"}), auditLog);

        AuditEvent event = repository.findByActor("USER", "alice").get(0);
        assertEquals("USER", event.actorType());
        assertTrue(event.detail().contains("138****5678"));
        assertFalse(event.detail().contains("plain"));
    }

    private ObjectProvider<AuditLogRepository> provider(AuditLogRepository repository) {
        return new ObjectProvider<>() {
            @Override
            public AuditLogRepository getObject(Object... args) throws BeansException { return repository; }
            @Override
            public AuditLogRepository getIfAvailable() throws BeansException { return repository; }
            @Override
            public AuditLogRepository getIfUnique() throws BeansException { return repository; }
            @Override
            public AuditLogRepository getObject() throws BeansException { return repository; }
        };
    }

    private static class AuditedService {
        @AuditLog(value = "operate", eventType = "TEST")
        void operate(String value) {
        }
    }

    private static class SimpleJoinPoint implements ProceedingJoinPoint {
        private final Object[] args;
        SimpleJoinPoint(Object[] args) { this.args = args; }
        @Override public Object proceed() { return "ok"; }
        @Override public Object proceed(Object[] args) { return "ok"; }
        @Override public void set$AroundClosure(AroundClosure arc) { }
        @Override public String toShortString() { return "test"; }
        @Override public String toLongString() { return "test"; }
        @Override public Object getThis() { return this; }
        @Override public Object getTarget() { return this; }
        @Override public Object[] getArgs() { return args; }
        @Override public Signature getSignature() { return null; }
        @Override public SourceLocation getSourceLocation() { return null; }
        @Override public String getKind() { return "method-execution"; }
        @Override public StaticPart getStaticPart() { return null; }
    }
}

