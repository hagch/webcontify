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
    void shouldRejectPlatformAdminWithTenantId() {
        UUID tenantId = UUID.randomUUID();

        assertThatThrownBy(() -> new TenantInformation(tenantId, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Platform admin must not have a tenant ID");
    }

    @Test
    void shouldRejectNonPlatformAdminWithoutTenantId() {
        assertThatThrownBy(() -> new TenantInformation(null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant ID is required for non-platform-admin");
    }
}
