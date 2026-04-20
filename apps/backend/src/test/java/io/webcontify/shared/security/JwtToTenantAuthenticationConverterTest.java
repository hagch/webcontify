package io.webcontify.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtToTenantAuthenticationConverterTest {

    private JwtToTenantAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JwtToTenantAuthenticationConverter();
    }

    @Test
    void shouldConvertPlatformAdminJwtToAuthentication() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("admin-user-sub");
        when(jwt.getClaim("realm_access"))
                .thenReturn(Map.of("roles", List.of("PLATFORM_ADMIN", "default-roles-webcontify")));
        when(jwt.getClaim("tenant_id")).thenReturn(null);

        AbstractAuthenticationToken result = converter.convert(jwt);

        assertThat(result).isInstanceOf(TenantAwareAuthentication.class);
        TenantAwareAuthentication auth = (TenantAwareAuthentication) result;
        assertThat(auth.getPrincipal()).isEqualTo("admin-user-sub");
        assertThat(auth.getTenantInformation().platformAdmin()).isTrue();
        assertThat(auth.getTenantInformation().tenantId()).isNull();
        assertThat(auth.getAuthorities())
                .contains(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void shouldConvertTenantUserJwtToAuthentication() {
        UUID tenantId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("tenant-user-sub");
        when(jwt.getClaim("realm_access"))
                .thenReturn(Map.of("roles", List.of("default-roles-webcontify")));
        when(jwt.getClaim("tenant_id")).thenReturn(tenantId.toString());

        AbstractAuthenticationToken result = converter.convert(jwt);

        assertThat(result).isInstanceOf(TenantAwareAuthentication.class);
        TenantAwareAuthentication auth = (TenantAwareAuthentication) result;
        assertThat(auth.getPrincipal()).isEqualTo("tenant-user-sub");
        assertThat(auth.getTenantInformation().platformAdmin()).isFalse();
        assertThat(auth.getTenantInformation().tenantId()).isEqualTo(tenantId);
        assertThat(auth.getAuthorities()).isEmpty();
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenTenantUserHasNoTenantId() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("tenant-user-sub");
        when(jwt.getClaim("realm_access"))
                .thenReturn(Map.of("roles", List.of("default-roles-webcontify")));
        when(jwt.getClaim("tenant_id")).thenReturn(null);

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant_id");
    }

    @Test
    void shouldHandleMissingRealmAccessClaim() {
        UUID tenantId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("tenant-user-sub");
        when(jwt.getClaim("realm_access")).thenReturn(null);
        when(jwt.getClaim("tenant_id")).thenReturn(tenantId.toString());

        AbstractAuthenticationToken result = converter.convert(jwt);

        assertThat(result).isInstanceOf(TenantAwareAuthentication.class);
        TenantAwareAuthentication auth = (TenantAwareAuthentication) result;
        assertThat(auth.getTenantInformation().platformAdmin()).isFalse();
        assertThat(auth.getTenantInformation().tenantId()).isEqualTo(tenantId);
    }

    @Test
    void shouldHandleInvalidTenantIdFormat() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("tenant-user-sub");
        when(jwt.getClaim("realm_access"))
                .thenReturn(Map.of("roles", List.of("default-roles-webcontify")));
        when(jwt.getClaim("tenant_id")).thenReturn("not-a-valid-uuid");

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant_id");
    }

    @Test
    void shouldNotGrantPlatformAdminAuthorityToRegularTenantUser() {
        UUID tenantId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("tenant-user-sub");
        when(jwt.getClaim("realm_access"))
                .thenReturn(Map.of("roles", List.of("some-other-role")));
        when(jwt.getClaim("tenant_id")).thenReturn(tenantId.toString());

        AbstractAuthenticationToken result = converter.convert(jwt);

        assertThat(result.getAuthorities())
                .doesNotContain(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }
}
