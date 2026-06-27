package com.platform.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.platform.common.auth.AuthPrincipal;
import com.platform.common.exception.BusinessException;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequirePermissionAspectTest {
    private final RequirePermissionAspect aspect = new RequirePermissionAspect();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindPrincipal(AuthPrincipal principal) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/partners");
        if (principal != null) {
            request.setAttribute(JwtAuthFilter.PRINCIPAL_ATTR, principal);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void allowsWhenPermissionPresent() throws Throwable {
        bindPrincipal(new AuthPrincipal("alice", Set.of("partner:view"), 9999999999L));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.check(joinPoint, sample("partner:view"));

        assertEquals("ok", result);
    }

    @Test
    void forbidsWhenPermissionMissing() {
        bindPrincipal(new AuthPrincipal("alice", Set.of(), 9999999999L));
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.check(joinPoint, sample("partner:view")));
        assertEquals("AUTH-403", ex.code());
    }

    @Test
    void unauthorizedWhenNoPrincipal() {
        bindPrincipal(null);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.check(joinPoint, sample("partner:view")));
        assertEquals("AUTH-401", ex.code());
    }

    private RequirePermission sample(String code) {
        return new RequirePermission() {
            @Override
            public String value() {
                return code;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RequirePermission.class;
            }
        };
    }
}
