package io.webcontify.identity.security;

import io.webcontify.identity.model.CheckPermissionRequest;
import io.webcontify.identity.service.PermissionService;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionCheckAspectTest {

    @Mock
    private PermissionService permissionService;

    private TestService testService;

    static class TestService {
        @RequiresPermission(action = "reader", resource = "user_management")
        public String doProtectedAction() {
            return "executed";
        }

        @RequiresPermission(action = "admin", resource = "tenant", resourceId = "#tenantId")
        public String doActionWithResourceId(UUID tenantId) {
            return "executed";
        }

        public String doUnprotectedAction() {
            return "executed";
        }
    }

    @BeforeEach
    void setUp() {
        var aspect = new PermissionCheckAspect(permissionService);
        var factory = new AspectJProxyFactory(new TestService());
        factory.addAspect(aspect);
        testService = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUpSecurityContext(UUID userId, boolean isPlatformAdmin) {
        var tenantInfo = isPlatformAdmin
                ? new TenantInformation(null, true)
                : new TenantInformation(UUID.randomUUID(), false);
        var auth = new TenantAwareAuthentication(tenantInfo, userId, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void shouldAllowWhenUserHasPermission() {
        var userId = UUID.randomUUID();
        setUpSecurityContext(userId, false);
        when(permissionService.check(any())).thenReturn(true);

        var result = testService.doProtectedAction();

        assertThat(result).isEqualTo("executed");
        verify(permissionService).check(any(CheckPermissionRequest.class));
    }

    @Test
    void shouldDenyWhenUserLacksPermission() {
        setUpSecurityContext(UUID.randomUUID(), false);
        when(permissionService.check(any())).thenReturn(false);

        assertThatThrownBy(() -> testService.doProtectedAction())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldBypassCheckForPlatformAdmin() {
        setUpSecurityContext(UUID.randomUUID(), true);

        var result = testService.doProtectedAction();

        assertThat(result).isEqualTo("executed");
        verifyNoInteractions(permissionService);
    }

    @Test
    void shouldExecuteUnprotectedActionWithoutCheck() {
        // kein SecurityContext nötig, kein Check erwartet
        var result = testService.doUnprotectedAction();

        assertThat(result).isEqualTo("executed");
        verifyNoInteractions(permissionService);
    }

    @Test
    void shouldResolveSpelResourceId() {
        var userId = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        setUpSecurityContext(userId, false);
        when(permissionService.check(any())).thenReturn(true);

        testService.doActionWithResourceId(tenantId);

        verify(permissionService).check(argThat(req ->
                req.resourceId().equals(tenantId) && req.userId().equals(userId)));
    }
}
