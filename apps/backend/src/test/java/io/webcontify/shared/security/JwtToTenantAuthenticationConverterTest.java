package io.webcontify.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class JwtToTenantAuthenticationConverterTest {

    @Mock
    private TenantSlugResolver tenantSlugResolver;

    private JwtToTenantAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JwtToTenantAuthenticationConverter(tenantSlugResolver);
    }

    @Test
    void shouldConvertPlatformAdminJwtToAuthentication() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("00000000-0000-0000-0000-000000000001");
        when(jwt.getClaim("realm_access"))
                .thenReturn(Map.of("roles", List.of("PLATFORM_ADMIN", "default-roles-webcontify")));

        AbstractAuthenticationToken result = converter.convert(jwt);

        assertThat(result).isInstanceOf(TenantAwareAuthentication.class);
        TenantAwareAuthentication auth = (TenantAwareAuthentication) result;
        assertThat(auth.getPrincipal()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(auth.getTenantInformation().platformAdmin()).isTrue();
        assertThat(auth.getTenantInformation().tenantId()).isNull();
        assertThat(auth.getAuthorities())
                .contains(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void shouldConvertTenantUserJwtWithTenantIdClaim() {
        UUID tenantId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("00000000-0000-0000-0000-000000000002");
        when(jwt.getClaim("realm_access"))
                .thenReturn(Map.of("roles", List.of("default-roles-webcontify")));
        when(jwt.getClaim("tenant_id")).thenReturn(tenantId.toString());
        when(tenantSlugResolver.resolveSlugByTenantId(tenantId)).thenReturn(Optional.of("acme"));

        AbstractAuthenticationToken result = converter.convert(jwt);

        assertThat(result).isInstanceOf(TenantAwareAuthentication.class);
        TenantAwareAuthentication auth = (TenantAwareAuthentication) result;
        assertThat(auth.getPrincipal()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(auth.getTenantInformation().platformAdmin()).isFalse();
        assertThat(auth.getTenantInformation().tenantId()).isEqualTo(tenantId);
        assertThat(auth.getTenantInformation().slug()).isEqualTo("acme");
        assertThat(auth.getAuthorities()).isEmpty();
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void shouldFallbackToIssuerWhenTenantIdClaimMissing() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("00000000-0000-0000-0000-000000000002");
        when(jwt.getClaim("realm_access"))
                .thenReturn(Map.of("roles", List.of("default-roles-webcontify")));
        when(jwt.getClaim("tenant_id")).thenReturn(null);
        when(jwt.getIssuer()).thenReturn(new URL("http://auth.localhost/realms/tenant-acme"));
        when(tenantSlugResolver.resolveTenantIdBySlug("acme")).thenReturn(Optional.of(tenantId));
        when(tenantSlugResolver.resolveSlugByTenantId(tenantId)).thenReturn(Optional.of("acme"));

        AbstractAuthenticationToken result = converter.convert(jwt);

        TenantAwareAuthentication auth = (TenantAwareAuthentication) result;
        assertThat(auth.getTenantInformation().tenantId()).isEqualTo(tenantId);
        assertThat(auth.getTenantInformation().platformAdmin()).isFalse();
        assertThat(auth.getTenantInformation().slug()).isEqualTo("acme");
    }

    @Test
    void shouldThrowWhenNoTenantIdAndNoIssuerFallback() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("00000000-0000-0000-0000-000000000002");
        when(jwt.getClaim("realm_access"))
                .thenReturn(Map.of("roles", List.of("default-roles-webcontify")));
        when(jwt.getClaim("tenant_id")).thenReturn(null);
        when(jwt.getIssuer()).thenReturn(null);

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant_id");
    }

    @Test
    void shouldThrowWhenIssuerIsNotTenantRealm() throws Exception {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("00000000-0000-0000-0000-000000000002");
        when(jwt.getClaim("realm_access"))
                .thenReturn(Map.of("roles", List.of("default-roles-webcontify")));
        when(jwt.getClaim("tenant_id")).thenReturn(null);
        when(jwt.getIssuer()).thenReturn(new URL("http://auth.localhost/realms/webcontify-platform"));

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant_id");
    }

    @Test
    void shouldHandleMissingRealmAccessClaim() {
        UUID tenantId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("00000000-0000-0000-0000-000000000002");
        when(jwt.getClaim("realm_access")).thenReturn(null);
        when(jwt.getClaim("tenant_id")).thenReturn(tenantId.toString());
        when(tenantSlugResolver.resolveSlugByTenantId(tenantId)).thenReturn(Optional.of("test-tenant"));

        AbstractAuthenticationToken result = converter.convert(jwt);

        TenantAwareAuthentication auth = (TenantAwareAuthentication) result;
        assertThat(auth.getTenantInformation().platformAdmin()).isFalse();
        assertThat(auth.getTenantInformation().tenantId()).isEqualTo(tenantId);
        assertThat(auth.getTenantInformation().slug()).isEqualTo("test-tenant");
    }

    @Test
    void shouldHandleInvalidTenantIdFormatWithIssuerFallback() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("00000000-0000-0000-0000-000000000002");
        when(jwt.getClaim("realm_access"))
                .thenReturn(Map.of("roles", List.of("default-roles-webcontify")));
        when(jwt.getClaim("tenant_id")).thenReturn("not-a-valid-uuid");
        when(jwt.getIssuer()).thenReturn(new URL("http://auth.localhost/realms/tenant-acme"));
        when(tenantSlugResolver.resolveTenantIdBySlug("acme")).thenReturn(Optional.of(tenantId));
        when(tenantSlugResolver.resolveSlugByTenantId(tenantId)).thenReturn(Optional.of("acme"));

        AbstractAuthenticationToken result = converter.convert(jwt);

        TenantAwareAuthentication auth = (TenantAwareAuthentication) result;
        assertThat(auth.getTenantInformation().tenantId()).isEqualTo(tenantId);
        assertThat(auth.getTenantInformation().slug()).isEqualTo("acme");
    }

    @Test
    void shouldNotGrantPlatformAdminAuthorityToRegularTenantUser() {
        UUID tenantId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("00000000-0000-0000-0000-000000000002");
        when(jwt.getClaim("realm_access"))
                .thenReturn(Map.of("roles", List.of("some-other-role")));
        when(jwt.getClaim("tenant_id")).thenReturn(tenantId.toString());
        when(tenantSlugResolver.resolveSlugByTenantId(tenantId)).thenReturn(Optional.of("test"));

        AbstractAuthenticationToken result = converter.convert(jwt);

        assertThat(result.getAuthorities())
                .doesNotContain(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }
}
