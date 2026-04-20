package io.webcontify.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.tenant.model.Tenant;
import io.webcontify.tenant.model.TenantStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTest {

    @Mock
    private TenantService tenantService;

    @Mock
    private FileStorageService fileStorageService;

    private TenantProvisioningService provisioningService;

    @BeforeEach
    void setUp() {
        provisioningService = new TenantProvisioningService(tenantService, fileStorageService);
    }

    @Test
    void shouldCreateTenantAndBucketAndSetStatusActive() {
        // Arrange
        Tenant input = new Tenant();
        input.setName("Acme Corp");
        input.setSlug("acme-corp");

        Tenant created = new Tenant();
        created.setId(UUID.randomUUID());
        created.setName("Acme Corp");
        created.setSlug("acme-corp");
        created.setStatus(TenantStatus.INACTIVE);

        Tenant activated = new Tenant();
        activated.setId(created.getId());
        activated.setName("Acme Corp");
        activated.setSlug("acme-corp");
        activated.setStatus(TenantStatus.ACTIVE);

        when(tenantService.create(any(Tenant.class))).thenReturn(created);
        when(tenantService.updateStatus(created.getId(), TenantStatus.ACTIVE)).thenReturn(activated);

        // Act
        Tenant result = provisioningService.provision(input);

        // Assert
        assertThat(result.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        verify(fileStorageService).createBucket("tenant-acme-corp");
        verify(tenantService).updateStatus(created.getId(), TenantStatus.ACTIVE);
    }

    @Test
    void shouldReturnInactiveTenantWhenBucketCreationFails() {
        // Arrange
        Tenant input = new Tenant();
        input.setName("Broken Corp");
        input.setSlug("broken-corp");

        Tenant created = new Tenant();
        created.setId(UUID.randomUUID());
        created.setName("Broken Corp");
        created.setSlug("broken-corp");
        created.setStatus(TenantStatus.INACTIVE);

        when(tenantService.create(any(Tenant.class))).thenReturn(created);
        doThrow(new RuntimeException("S3 nicht erreichbar"))
                .when(fileStorageService)
                .createBucket("tenant-broken-corp");

        // Act — kein Fehler erwartet
        Tenant result = provisioningService.provision(input);

        // Assert
        assertThat(result.getStatus()).isEqualTo(TenantStatus.INACTIVE);
        verify(fileStorageService).createBucket("tenant-broken-corp");
        // updateStatus darf NICHT aufgerufen werden, da Bucket-Erstellung gescheitert
        verify(tenantService, never()).updateStatus(any(), eq(TenantStatus.ACTIVE));
    }
}
