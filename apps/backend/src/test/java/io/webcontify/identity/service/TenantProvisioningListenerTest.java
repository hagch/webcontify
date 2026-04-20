package io.webcontify.identity.service;

import io.webcontify.identity.config.PlatformSmtpProperties;
import io.webcontify.identity.model.*;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.model.ProvisioningStatus;
import io.webcontify.shared.model.ProvisioningStepStatus;
import io.webcontify.shared.model.ProvisioningStepType;
import io.webcontify.tenant.event.TenantCreatedEvent;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningListenerTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private OpenFgaService openFgaService;

    private TenantProvisioningListener listener;

    @BeforeEach
    void setUp() {
        var smtpProps = new PlatformSmtpProperties("localhost", 1025, "", "", false);
        listener = new TenantProvisioningListener(
                tenantRepository, fileStorageService, keycloakAdminService, openFgaService, smtpProps);
    }

    @Test
    void shouldExecuteAllStepsAndSetStatusActive() {
        var tenantId = UUID.randomUUID();
        var event = new TenantCreatedEvent(tenantId, "acme", "admin@acme.com", "Max", "Muster");

        var entity = createEntityWithInitialStatus(tenantId, event);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(entity));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(keycloakAdminService.createRealm(any())).thenReturn(new CreateRealmResponse("tenant-acme"));
        when(keycloakAdminService.createUser(any())).thenReturn(new CreateKeycloakUserResponse(UUID.randomUUID()));
        when(openFgaService.createStore(any())).thenReturn(new CreateStoreResponse("store-123"));

        listener.onTenantCreated(event);

        verify(fileStorageService).createBucket("tenant-acme");
        verify(keycloakAdminService).createRealm(any());
        verify(openFgaService).createStore(any());
        verify(keycloakAdminService).createUser(any());
        verify(keycloakAdminService).sendInviteEmail(any());
        verify(openFgaService).write(any());

        var savedCaptor = ArgumentCaptor.forClass(TenantEntity.class);
        verify(tenantRepository).save(savedCaptor.capture());
        var saved = savedCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TenantStatus.ACTIVE.name());
        assertThat(saved.getKeycloakRealmName()).isEqualTo("tenant-acme");
        assertThat(saved.getOpenfgaStoreId()).isEqualTo("store-123");
    }

    @Test
    void shouldMarkStepAsFailedAndKeepProvisioningStatusOnS3Error() {
        var tenantId = UUID.randomUUID();
        var event = new TenantCreatedEvent(tenantId, "acme", "admin@acme.com", "Max", "Muster");

        var entity = createEntityWithInitialStatus(tenantId, event);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(entity));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("S3 error")).when(fileStorageService).createBucket(any());

        // Should not throw — provisioning failure is handled gracefully
        listener.onTenantCreated(event);

        var savedCaptor = ArgumentCaptor.forClass(TenantEntity.class);
        verify(tenantRepository).save(savedCaptor.capture());
        var saved = savedCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TenantStatus.PROVISIONING.name());

        var status = ProvisioningStatus.fromJson(saved.getProvisioningStatus());
        assertThat(status.steps().get(0).status()).isEqualTo(ProvisioningStepStatus.FAILED);
        assertThat(status.steps().get(0).step()).isEqualTo(ProvisioningStepType.S3_BUCKET);
    }

    @Test
    void shouldSkipCompletedSteps() {
        var tenantId = UUID.randomUUID();
        var event = new TenantCreatedEvent(tenantId, "acme", "admin@acme.com", "Max", "Muster");

        // S3 already completed, rest pending
        var entity = createEntityWithPartialStatus(tenantId, event);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(entity));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(keycloakAdminService.createRealm(any())).thenReturn(new CreateRealmResponse("tenant-acme"));
        when(keycloakAdminService.createUser(any())).thenReturn(new CreateKeycloakUserResponse(UUID.randomUUID()));
        when(openFgaService.createStore(any())).thenReturn(new CreateStoreResponse("store-456"));

        listener.onTenantCreated(event);

        // S3 bucket must NOT be created again
        verifyNoInteractions(fileStorageService);
        verify(keycloakAdminService).createRealm(any());
    }

    private TenantEntity createEntityWithInitialStatus(UUID tenantId, TenantCreatedEvent event) {
        var entity = new TenantEntity();
        entity.setId(tenantId);
        entity.setSlug(event.slug());
        entity.setStatus(TenantStatus.PROVISIONING.name());
        entity.setProvisioningStatus(
                ProvisioningStatus.initial(event.adminEmail(), event.adminFirstName(), event.adminLastName()).toJson()
        );
        return entity;
    }

    private TenantEntity createEntityWithPartialStatus(UUID tenantId, TenantCreatedEvent event) {
        var steps = List.of(
                io.webcontify.shared.model.ProvisioningStep.pending(ProvisioningStepType.S3_BUCKET).completed(),
                io.webcontify.shared.model.ProvisioningStep.pending(ProvisioningStepType.KEYCLOAK_REALM),
                io.webcontify.shared.model.ProvisioningStep.pending(ProvisioningStepType.OPENFGA_STORE),
                io.webcontify.shared.model.ProvisioningStep.pending(ProvisioningStepType.INITIAL_ADMIN)
        );
        var status = new ProvisioningStatus(steps, event.adminEmail(), event.adminFirstName(), event.adminLastName());
        var entity = new TenantEntity();
        entity.setId(tenantId);
        entity.setSlug(event.slug());
        entity.setStatus(TenantStatus.PROVISIONING.name());
        entity.setProvisioningStatus(status.toJson());
        return entity;
    }
}
