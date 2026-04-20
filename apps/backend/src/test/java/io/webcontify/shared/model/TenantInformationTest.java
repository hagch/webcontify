package io.webcontify.shared.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantInformationTest {

    @Test
    void shouldCreateTenantInformationWithTenantId() {
        UUID tenantId = UUID.randomUUID();
        TenantInformation info = new TenantInformation(tenantId, false);

        assertThat(info.tenantId()).isEqualTo(tenantId);
        assertThat(info.platformAdmin()).isFalse();
    }

    @Test
    void shouldCreatePlatformAdminWithNullTenantId() {
        TenantInformation info = new TenantInformation(null, true);

        assertThat(info.tenantId()).isNull();
        assertThat(info.platformAdmin()).isTrue();
    }

    @Test
    void shouldAllowPlatformAdminWithTenantId() {
        UUID tenantId = UUID.randomUUID();

        // Platform admins can have a tenantId when operating on a tenant subdomain
        TenantInformation info = new TenantInformation(tenantId, true);

        assertThat(info.tenantId()).isEqualTo(tenantId);
        assertThat(info.platformAdmin()).isTrue();
    }

    @Test
    void shouldRejectNonPlatformAdminWithoutTenantId() {
        assertThatThrownBy(() -> new TenantInformation(null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant ID is required for non-platform-admin");
    }

    @Test
    void shouldCreateTenantInformationWithSlug() {
        UUID tenantId = UUID.randomUUID();
        TenantInformation info = new TenantInformation(tenantId, false, "tenant-acme", "acme");

        assertThat(info.tenantId()).isEqualTo(tenantId);
        assertThat(info.platformAdmin()).isFalse();
        assertThat(info.realmName()).isEqualTo("tenant-acme");
        assertThat(info.slug()).isEqualTo("acme");
    }

    @Test
    void shouldAllowNullSlugForPlatformAdmin() {
        TenantInformation info = new TenantInformation(null, true);

        assertThat(info.slug()).isNull();
        assertThat(info.platformAdmin()).isTrue();
    }

    @Test
    void shouldAllowNullSlugWithThreeArgConstructor() {
        UUID tenantId = UUID.randomUUID();
        TenantInformation info = new TenantInformation(tenantId, false, "tenant-acme");

        assertThat(info.slug()).isNull();
        assertThat(info.realmName()).isEqualTo("tenant-acme");
    }
}
