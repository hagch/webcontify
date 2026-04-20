package io.webcontify.identity.service;

import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.tenant.model.TenantInfrastructureInfo;
import io.webcontify.tenant.service.TenantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantInfrastructureLookupServiceTest {

    @Mock
    private TenantService tenantService;

    @InjectMocks
    private TenantInfrastructureLookupService lookupService;

    @Test
    void shouldReturnStoreIdForTenant() {
        var tenantId = UUID.randomUUID();
        var info = new TenantInfrastructureInfo("store-123", "tenant-acme");
        when(tenantService.getInfrastructureInfo(tenantId)).thenReturn(info);

        var result = lookupService.getInfrastructure(tenantId);

        assertThat(result.openfgaStoreId()).isEqualTo("store-123");
        assertThat(result.keycloakRealmName()).isEqualTo("tenant-acme");
    }

    @Test
    void shouldThrowWhenTenantNotFound() {
        var tenantId = UUID.randomUUID();
        when(tenantService.getInfrastructureInfo(tenantId))
                .thenThrow(new NotFoundException("tenant.not_found"));

        assertThatThrownBy(() -> lookupService.getInfrastructure(tenantId))
            .isInstanceOf(NotFoundException.class);
    }
}
