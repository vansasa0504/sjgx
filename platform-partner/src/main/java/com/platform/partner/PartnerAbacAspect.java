package com.platform.partner;

import com.platform.common.auth.AuthPrincipal;
import com.platform.common.exception.BusinessException;
import com.platform.common.model.Result;
import com.platform.common.security.DesensitizeUtil;
import com.platform.common.security.JwtAuthFilter;
import com.platform.partner.consumer.Consumer;
import com.platform.partner.consumer.ConsumerService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class PartnerAbacAspect {
    private final ConsumerService consumers;

    public PartnerAbacAspect(ConsumerService consumers) {
        this.consumers = consumers;
    }

    @Around("(execution(* com.platform.partner.consumer.ConsumerController.detail(..))"
            + " || execution(* com.platform.partner.consumer.ConsumerController.audit(..))"
            + " || execution(* com.platform.partner.consumer.ConsumerController.logs(..))) && args(id,..)")
    public Object consumerOwnership(ProceedingJoinPoint joinPoint, long id) throws Throwable {
        AuthPrincipal principal = requiredPrincipal();
        boolean administrative = principal.hasPermission("system:view")
                || principal.hasPermission("system:update");
        if (!administrative && !consumers.find(id).consumerCode().equals(principal.username())) {
            throw new BusinessException("AUTH-403", "resource access denied");
        }
        return joinPoint.proceed();
    }

    @Around("execution(* com.platform.partner.consumer.ConsumerController.configureQuota(..))")
    public Object quotaAdministration(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!requiredPrincipal().hasPermission("system:update")) {
            throw new BusinessException("AUTH-403", "quota administration denied");
        }
        return joinPoint.proceed();
    }

    @Around("execution(* com.platform.partner.consumer.ConsumerController.list(..))")
    public Object consumerList(ProceedingJoinPoint joinPoint) throws Throwable {
        AuthPrincipal principal = requiredPrincipal();
        @SuppressWarnings("unchecked")
        Result<List<Consumer>> result = (Result<List<Consumer>>) joinPoint.proceed();
        if (principal.hasPermission("system:view")) return result;
        List<Consumer> own = result.data().stream()
                .filter(value -> value.consumerCode().equals(principal.username())).toList();
        return new Result<>(result.success(), result.code(), result.message(), own);
    }

    @Around("execution(* com.platform.partner.PartnerController.interfaces(..))")
    public Object credentialField(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        AuthPrincipal principal = requiredPrincipal();
        if (principal.hasPermission("system:view")) return result;
        @SuppressWarnings("unchecked")
        Result<List<PartnerInterfaceConfig>> typed = (Result<List<PartnerInterfaceConfig>>) result;
        List<PartnerInterfaceConfig> masked = typed.data().stream()
                .map(value -> new PartnerInterfaceConfig(value.partnerId(), value.protocol(), value.endpoint(),
                        DesensitizeUtil.replace(value.encryptedCredential(), "****")))
                .toList();
        return new Result<>(typed.success(), typed.code(), typed.message(), masked);
    }

    private AuthPrincipal requiredPrincipal() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) throw new BusinessException("AUTH-401", "unauthorized");
        HttpServletRequest request = attrs.getRequest();
        Object value = request.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        if (value instanceof AuthPrincipal auth) return auth;
        throw new BusinessException("AUTH-401", "unauthorized");
    }
}