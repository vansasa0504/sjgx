package com.platform.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明被注解方法所需的权限码。由 {@link RequirePermissionAspect} 在调用前校验，
 * 当前登录用户（{@code AuthPrincipal}）缺少该权限码时抛出 AUTH-403。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String value();
}
