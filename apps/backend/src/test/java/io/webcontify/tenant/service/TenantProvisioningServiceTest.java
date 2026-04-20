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

        Tenant created = new Tenant();
        created.setId(UUID.randomUUID());
        created.setName("Acme Corp");
        created.setSlug("acme-corp");
        created.setStatus(TenantStatus.PROVISIONING);

        Tenant activated = new Tenant();
        activated.setId(created.getId());
        activated.setName("Acme Corp");
        activated.setSlug("acme-corp");
        activated.setStatus(TenantStatus.ACTIVE);

        when(tenantService.create(any(Tenant.class), eq("admin@acme.com"), eq("John"), eq("Doe")))
                .thenReturn(created);
        when(tenantService.updateStatus(created.getId(), TenantStatus.ACTIVE)).thenReturn(activated);

        // Act
        Tenant result = provisioningService.provision(input, "admin@acme.com", "John", "Doe");

        // Assert
        assertThat(result.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        verify(fileStorageService).createBucket("tenant-acme-corp");
        verify(tenantService).updateStatus(created.getId(), TenantStatus.ACTIVE);
    }

    @Test
    void shouldReturnProvisioningTenantWhenBucketCreationFails() {
        // Arrange
        Tenant input = new Tenant();
        input.setName("Broken Corp");

        Tenant created = new Tenant();
        created.setId(UUID.randomUUID());
        created.setName("Broken Corp");
        created.setSlug("broken-corp");
        created.setStatus(TenantStatus.PROVISIONING);

        when(tenantService.create(any(Tenant.class), eq("admin@broken.com"), eq("Jane"), eq("Doe")))
                .thenReturn(created);
        doThrow(new RuntimeException("S3 nicht erreichbar"))
                .when(fileStorageService)
                .createBucket("tenant-broken-corp");

        // Act — kein Fehler erwartet
        Tenant result = provisioningService.provision(input, "admin@broken.com", "Jane", "Doe");

        // Assert
        assertThat(result.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        verify(fileStorageService).createBucket("tenant-broken-corp");
        // updateStatus darf NICHT aufgerufen werden, da Bucket-Erstellung gescheitert
        verify(tenantService, never()).updateStatus(any(), eq(TenantStatus.ACTIVE));
    }
}
