package br.com.vortex.application.config.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Read access: every role the authorization service issues (VIEWER, USER, MANAGER and ADMIN).
 * <p>
 * On a handler method it overrides the class-level annotation, which is how the GET endpoints of a
 * {@link CanWrite} controller open up to VIEWER. The roles are listed explicitly, without a
 * {@code RoleHierarchy}: see docs/security/roles.md.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('VIEWER', 'USER', 'MANAGER', 'ADMIN')")
public @interface CanRead {
}
