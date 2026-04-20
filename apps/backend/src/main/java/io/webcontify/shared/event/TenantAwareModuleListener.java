package io.webcontify.shared.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.modulith.events.ApplicationModuleListener;

/**
 * Drop-in replacement for {@link ApplicationModuleListener} for event handlers
 * that need tenant-scoped JPA access. The event parameter must implement
 * {@link TenantScopedEvent}.
 *
 * <p>An AOP aspect ({@link TenantAwareModuleListenerAspect}) intercepts methods
 * annotated with this annotation and sets the tenant SecurityContext <em>before</em>
 * the transaction opens, so Hibernate's RLS connection provider resolves the
 * correct {@code app.tenant_id}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApplicationModuleListener
public @interface TenantAwareModuleListener {
}
