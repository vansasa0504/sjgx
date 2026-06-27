package com.platform.common.security;

import com.platform.common.auth.AuthPrincipal;
import com.platform.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 校验被 {@link RequirePermission} 注解方法所需权限码。当前登录用户缺少权限码时抛出
 * {@code AUTH-403}（由 {@code GlobalExceptionHandler} 映射为 403）。
 */
@Aspect
public class RequirePermissionAspect {

    @Around("@annotation(requirePermission)")
    public Object check(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        AuthPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw new BusinessException("AUTH-401", "unauthorized");
        }
        if (!principal.hasPermission(requirePermission.value())) {
            throw new BusinessException("AUTH-403", "forbidden");
        }
        return joinPoint.proceed();
    }

    private AuthPrincipal currentPrincipal() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        Object value = request.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        return value instanceof AuthPrincipal principal ? principal : null;
    }
}
