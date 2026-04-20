package io.webcontify.shared.event;

import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Sets the tenant SecurityContext before the method (and its transaction) executes,
 * ensuring that Hibernate's {@code RlsConnectionProvider} sets the correct
 * {@code app.tenant_id} on the JDBC connection.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} so it wraps the transaction
 * interceptor — the SecurityContext is established before any connection is acquired.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantAwareModuleListenerAspect {

    @Around("@annotation(io.webcontify.shared.event.TenantAwareModuleListener)")
    public Object setTenantContext(ProceedingJoinPoint joinPoint) throws Throwable {
        TenantScopedEvent event = extractTenantEvent(joinPoint);
        if (event != null) {
            var tenantInfo = new TenantInformation(event.tenantId(), false);
            var auth = new TenantAwareAuthentication(
                    tenantInfo, event.tenantId().toString(), List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        try {
            return joinPoint.proceed();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private TenantScopedEvent extractTenantEvent(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof TenantScopedEvent event) {
                return event;
            }
        }
        return null;
    }
}
