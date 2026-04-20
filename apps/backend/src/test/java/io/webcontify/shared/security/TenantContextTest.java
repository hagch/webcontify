package io.webcontify.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.webcontify.shared.model.TenantInformation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantContextTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnTenantInformationFromSecurityContext() {
        UUID tenantId = UUID.randomUUID();
        TenantInformation info = new TenantInformation(tenantId, false);
        var auth = new TenantAwareAuthentication(info, "user", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        TenantInformation result = TenantContext.getTenantInformation();

        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.platformAdmin()).isFalse();
    }

    @Test
    void shouldThrowWhenNoAuthenticationSet() {
        assertThatThrownBy(TenantContext::getTenantInformation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant information available");
    }
}
