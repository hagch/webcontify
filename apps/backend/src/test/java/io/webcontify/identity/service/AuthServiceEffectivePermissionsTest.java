package io.webcontify.identity.service;

import io.webcontify.identity.model.OpenFgaBatchCheckRequest;
import io.webcontify.identity.model.OpenFgaBatchCheckResponse;
import io.webcontify.shared.exception.BadRequestException;
import io.webcontify.shared.model.TenantInformation;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.shared.security.TenantContext;
import io.webcontify.tenant.model.TenantInfrastructureInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceEffectivePermissionsTest {

    @Mock
    private KeycloakAdminService keycloakAdminService;

    @Mock
    private io.webcontify.shared.filestorage.FileStorageService fileStorageService;

    @Mock
    private TenantInfrastructureLookupService tenantInfrastructureLookupService;

    @Mock
    private OpenFgaService openFgaService;

    @InjectMocks
    private AuthService authService;

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String STORE_ID = "store-abc";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void withTenantAuth(UUID userId, boolean platformAdmin) {
        var tenantInfo = platformAdmin
                ? new TenantInformation(null, true)
                : new TenantInformation(TENANT_ID, false);
        var auth = new TenantAwareAuthentication(tenantInfo, userId, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // -----------------------------------------------------------------------
    // getEffectivePermissions — navigation scope
    // -----------------------------------------------------------------------

    @Nested
    class NavigationScope {

        @Test
        void shouldReturnGrantedActionsForUserWithReaderOnUserManagement() {
            withTenantAuth(USER_ID, false);

            try (var mockedContext = mockStatic(TenantContext.class)) {
                mockedContext.when(TenantContext::getTenantInformation)
                        .thenReturn(new TenantInformation(TENANT_ID, false));

                when(tenantInfrastructureLookupService.getInfrastructure(TENANT_ID))
                        .thenReturn(new TenantInfrastructureInfo(STORE_ID, "tenant-acme"));

                // reader on user_management granted, everything else false
                var results = new java.util.HashMap<String, Boolean>();
                results.put("reader:user_management", true);
                results.put("inviter:user_management", false);
                results.put("editor:user_management", false);
                results.put("deleter:user_management", false);
                results.put("creator:role_management", false);
                results.put("editor:role_management", false);
                results.put("deleter:role_management", false);
                results.put("reader:role_management", false);
                results.put("editor:tenant_config", false);
                results.put("reader:tenant_config", false);
                results.put("admin:tenant", false);

                when(openFgaService.batchCheck(any(OpenFgaBatchCheckRequest.class)))
                        .thenReturn(new OpenFgaBatchCheckResponse(results));

                var permissions = authService.getEffectivePermissions("navigation");

                assertThat(permissions.permissions()).containsKey("user_management");
                assertThat(permissions.permissions().get("user_management")).containsExactly("reader");
                assertThat(permissions.permissions()).doesNotContainKey("role_management");
                assertThat(permissions.permissions()).doesNotContainKey("tenant_config");
                assertThat(permissions.tenantAdmin()).isFalse();
            }
        }

        @Test
        void shouldReturnAllPermissionsForPlatformAdmin() {
            withTenantAuth(USER_ID, true);

            try (var mockedContext = mockStatic(TenantContext.class)) {
                mockedContext.when(TenantContext::getTenantInformation)
                        .thenReturn(new TenantInformation(null, true));

                var permissions = authService.getEffectivePermissions("navigation");

                assertThat(permissions.tenantAdmin()).isTrue();
                assertThat(permissions.permissions()).containsKey("user_management");
                assertThat(permissions.permissions()).containsKey("role_management");
                assertThat(permissions.permissions()).containsKey("tenant_config");

                // OpenFGA must not be called for platform admins
                verify(openFgaService, never()).batchCheck(any());
            }
        }

        @Test
        void shouldReturnEmptyPermissionsMapWhenUserHasNoPermissions() {
            withTenantAuth(USER_ID, false);

            try (var mockedContext = mockStatic(TenantContext.class)) {
                mockedContext.when(TenantContext::getTenantInformation)
                        .thenReturn(new TenantInformation(TENANT_ID, false));

                when(tenantInfrastructureLookupService.getInfrastructure(TENANT_ID))
                        .thenReturn(new TenantInfrastructureInfo(STORE_ID, "tenant-acme"));

                var results = new java.util.HashMap<String, Boolean>();
                results.put("reader:user_management", false);
                results.put("inviter:user_management", false);
                results.put("editor:user_management", false);
                results.put("deleter:user_management", false);
                results.put("creator:role_management", false);
                results.put("editor:role_management", false);
                results.put("deleter:role_management", false);
                results.put("reader:role_management", false);
                results.put("editor:tenant_config", false);
                results.put("reader:tenant_config", false);
                results.put("admin:tenant", false);

                when(openFgaService.batchCheck(any(OpenFgaBatchCheckRequest.class)))
                        .thenReturn(new OpenFgaBatchCheckResponse(results));

                var permissions = authService.getEffectivePermissions("navigation");

                assertThat(permissions.permissions()).isEmpty();
                assertThat(permissions.tenantAdmin()).isFalse();
            }
        }

        @Test
        void shouldSetTenantAdminTrueWhenAdminTupleGranted() {
            withTenantAuth(USER_ID, false);

            try (var mockedContext = mockStatic(TenantContext.class)) {
                mockedContext.when(TenantContext::getTenantInformation)
                        .thenReturn(new TenantInformation(TENANT_ID, false));

                when(tenantInfrastructureLookupService.getInfrastructure(TENANT_ID))
                        .thenReturn(new TenantInfrastructureInfo(STORE_ID, "tenant-acme"));

                var results = new java.util.HashMap<String, Boolean>();
                results.put("reader:user_management", false);
                results.put("inviter:user_management", false);
                results.put("editor:user_management", false);
                results.put("deleter:user_management", false);
                results.put("creator:role_management", false);
                results.put("editor:role_management", false);
                results.put("deleter:role_management", false);
                results.put("reader:role_management", false);
                results.put("editor:tenant_config", false);
                results.put("reader:tenant_config", false);
                results.put("admin:tenant", true);

                when(openFgaService.batchCheck(any(OpenFgaBatchCheckRequest.class)))
                        .thenReturn(new OpenFgaBatchCheckResponse(results));

                var permissions = authService.getEffectivePermissions("navigation");

                assertThat(permissions.tenantAdmin()).isTrue();
            }
        }
    }

    // -----------------------------------------------------------------------
    // getEffectivePermissions — invalid scope
    // -----------------------------------------------------------------------

    @Nested
    class InvalidScope {

        @Test
        void shouldThrowBadRequestExceptionForUnknownScope() {
            withTenantAuth(USER_ID, false);

            try (var mockedContext = mockStatic(TenantContext.class)) {
                mockedContext.when(TenantContext::getTenantInformation)
                        .thenReturn(new TenantInformation(TENANT_ID, false));

                assertThatThrownBy(() -> authService.getEffectivePermissions("unknown"))
                        .isInstanceOf(BadRequestException.class);
            }
        }

        @Test
        void shouldAcceptNullScopeAsNavigation() {
            withTenantAuth(USER_ID, false);

            try (var mockedContext = mockStatic(TenantContext.class)) {
                mockedContext.when(TenantContext::getTenantInformation)
                        .thenReturn(new TenantInformation(TENANT_ID, false));

                when(tenantInfrastructureLookupService.getInfrastructure(TENANT_ID))
                        .thenReturn(new TenantInfrastructureInfo(STORE_ID, "tenant-acme"));

                var results = new java.util.HashMap<String, Boolean>();
                results.put("reader:user_management", false);
                results.put("inviter:user_management", false);
                results.put("editor:user_management", false);
                results.put("deleter:user_management", false);
                results.put("creator:role_management", false);
                results.put("editor:role_management", false);
                results.put("deleter:role_management", false);
                results.put("reader:role_management", false);
                results.put("editor:tenant_config", false);
                results.put("reader:tenant_config", false);
                results.put("admin:tenant", false);

                when(openFgaService.batchCheck(any(OpenFgaBatchCheckRequest.class)))
                        .thenReturn(new OpenFgaBatchCheckResponse(results));

                // null scope should not throw
                var permissions = authService.getEffectivePermissions(null);
                assertThat(permissions).isNotNull();
            }
        }
    }
}
