package br.com.vortex.application.config.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Write access (create, update and delete): USER, MANAGER and ADMIN, never VIEWER.
 * <p>
 * The CRUD controllers carry it at class level, so a handler added without an annotation fails
 * closed to write access; read handlers opt into {@link CanRead}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
public @interface CanWrite {
}
