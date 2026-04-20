package io.webcontify.identity.security;

import io.webcontify.identity.model.CheckPermissionRequest;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.shared.security.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class PermissionCheckAspect {

    private final PermissionService permissionService;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public PermissionCheckAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
        var tenantInfo = TenantContext.getTenantInformation();

        if (tenantInfo.platformAdmin()) {
            return joinPoint.proceed();
        }

        var authentication = (TenantAwareAuthentication)
                SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString((String) authentication.getPrincipal());

        UUID resourceId;
        if (!requiresPermission.resourceId().isEmpty()) {
            resourceId = evaluateSpel(joinPoint, requiresPermission.resourceId());
        } else {
            // Default to tenantId so singleton resources like user_management:{tenantId} resolve correctly
            resourceId = tenantInfo.tenantId();
        }

        var request = new CheckPermissionRequest(
                userId, requiresPermission.action(),
                requiresPermission.resource(), resourceId
        );

        if (!permissionService.check(request)) {
            throw new AccessDeniedException("Insufficient permissions");
        }

        return joinPoint.proceed();
    }

    private UUID evaluateSpel(ProceedingJoinPoint joinPoint, String expression) {
        var signature = (MethodSignature) joinPoint.getSignature();
        var paramNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());
        var context = new StandardEvaluationContext();

        if (paramNames != null) {
            var args = joinPoint.getArgs();
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        return spelParser.parseExpression(expression).getValue(context, UUID.class);
    }
}
