package io.webcontify.identity.service;

import io.webcontify.identity.config.PlatformSmtpProperties;
import io.webcontify.identity.model.*;
import io.webcontify.identity.repository.entity.TenantRoleEntity;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.model.ProvisioningStatus;
import io.webcontify.shared.model.ProvisioningStepStatus;
import io.webcontify.shared.model.ProvisioningStepType;
import io.webcontify.tenant.event.TenantCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningListenerTest {

    @Mock private ProvisioningStatusPersister statusPersister;
    @Mock private FileStorageService fileStorageService;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private OpenFgaService openFgaService;
    @Mock private io.webcontify.identity.repository.TenantUserRepository tenantUserRepository;
    @Mock private io.webcontify.identity.repository.TenantRoleRepository tenantRoleRepository;

    private TenantProvisioningListener listener;

    @BeforeEach
    void setUp() {
        var smtpProps = new PlatformSmtpProperties("localhost", 1025, "noreply@webcontify.io", "", "", false);
        listener = new TenantProvisioningListener(
                statusPersister, fileStorageService, keycloakAdminService, openFgaService, smtpProps,
                tenantUserRepository, tenantRoleRepository);
    }

    @Test
    void shouldExecuteAllStepsAndPersistAfterEachStep() {
        var tenantId = UUID.randomUUID();
        var event = new TenantCreatedEvent(tenantId, "acme", "admin@acme.com", "Max", "Muster");
        var initialStatus = ProvisioningStatus.initial("admin@acme.com", "Max", "Muster");

        when(statusPersister.loadSnapshot(tenantId))
                .thenReturn(new ProvisioningStatusPersister.ProvisioningSnapshot("Acme Corp", initialStatus));
        when(keycloakAdminService.createRealm(any())).thenReturn(new CreateRealmResponse("tenant-acme"));
        when(keycloakAdminService.createUser(any())).thenReturn(new CreateKeycloakUserResponse(UUID.randomUUID()));
        when(openFgaService.createStore(any())).thenReturn(new CreateStoreResponse("store-123"));
        when(tenantRoleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        listener.onTenantCreated(event);

        verify(fileStorageService).createBucket("tenant-acme");
        verify(keycloakAdminService).createRealm(any());
        verify(openFgaService).createStore(any());
        verify(keycloakAdminService).createUser(any());
        verify(keycloakAdminService).sendInviteEmail(any());
        verify(openFgaService).write(any());

        // 3 system roles must be saved (Admin, Editor, Viewer)
        verify(tenantRoleRepository, times(3)).save(any());

        // 4 persist calls: after step 0, 1, 2, and final completed
        verify(statusPersister, times(4)).persistStatus(eq(tenantId), any(), anyBoolean(), any());

        // Last call should be completed=true
        var statusCaptor = ArgumentCaptor.forClass(ProvisioningStatus.class);
        var completedCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(statusPersister, times(4)).persistStatus(eq(tenantId), statusCaptor.capture(),
                completedCaptor.capture(), any());
        var lastCompleted = completedCaptor.getAllValues().get(3);
        assertThat(lastCompleted).isTrue();

        var lastStatus = statusCaptor.getAllValues().get(3);
        assertThat(lastStatus.steps()).allMatch(s -> s.status() == ProvisioningStepStatus.COMPLETED);
    }

    @Test
    void shouldCreateThreeSystemRolesWithCorrectNames() {
        var tenantId = UUID.randomUUID();
        var event = new TenantCreatedEvent(tenantId, "acme", "admin@acme.com", "Max", "Muster");
        var initialStatus = ProvisioningStatus.initial("admin@acme.com", "Max", "Muster");

        when(statusPersister.loadSnapshot(tenantId))
                .thenReturn(new ProvisioningStatusPersister.ProvisioningSnapshot("Acme Corp", initialStatus));
        when(keycloakAdminService.createRealm(any())).thenReturn(new CreateRealmResponse("tenant-acme"));
        when(keycloakAdminService.createUser(any())).thenReturn(new CreateKeycloakUserResponse(UUID.randomUUID()));
        when(openFgaService.createStore(any())).thenReturn(new CreateStoreResponse("store-123"));
        when(tenantRoleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        listener.onTenantCreated(event);

        var roleCaptor = ArgumentCaptor.forClass(TenantRoleEntity.class);
        verify(tenantRoleRepository, times(3)).save(roleCaptor.capture());

        var savedRoles = roleCaptor.getAllValues();
        assertThat(savedRoles).extracting(TenantRoleEntity::getName)
                .containsExactlyInAnyOrder("Admin", "Editor", "Viewer");
        assertThat(savedRoles).allMatch(TenantRoleEntity::isSystemRole);
        assertThat(savedRoles).allMatch(r -> tenantId.equals(r.getTenantId()));
    }

    @Test
    void shouldWriteAllOpenFgaTuplesInOneBatch() {
        var tenantId = UUID.randomUUID();
        var keycloakUserId = UUID.randomUUID();
        var event = new TenantCreatedEvent(tenantId, "acme", "admin@acme.com", "Max", "Muster");
        var initialStatus = ProvisioningStatus.initial("admin@acme.com", "Max", "Muster");

        when(statusPersister.loadSnapshot(tenantId))
                .thenReturn(new ProvisioningStatusPersister.ProvisioningSnapshot("Acme Corp", initialStatus));
        when(keycloakAdminService.createRealm(any())).thenReturn(new CreateRealmResponse("tenant-acme"));
        when(keycloakAdminService.createUser(any())).thenReturn(new CreateKeycloakUserResponse(keycloakUserId));
        when(openFgaService.createStore(any())).thenReturn(new CreateStoreResponse("store-123"));
        when(tenantRoleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        listener.onTenantCreated(event);

        verify(openFgaService, times(1)).write(any());

        var writeCaptor = ArgumentCaptor.forClass(OpenFgaWriteRequest.class);
        verify(openFgaService).write(writeCaptor.capture());
        var request = writeCaptor.getValue();
        assertThat(request.storeId()).isEqualTo("store-123");

        var tuples = request.tuples();
        var tenantObj = "tenant:" + tenantId;
        var tenantStr = tenantId.toString();

        // Singleton resources linked to tenant
        assertThat(tuples).contains(
                new RelationshipTuple("tenant:" + tenantId, "tenant", "user_management:" + tenantId),
                new RelationshipTuple("tenant:" + tenantId, "tenant", "role_management:" + tenantId),
                new RelationshipTuple("tenant:" + tenantId, "tenant", "tenant_config:" + tenantId)
        );

        // tenant#admin tuple for the initial admin user must remain
        assertThat(tuples).contains(
                new RelationshipTuple("user:" + keycloakUserId, "admin", "tenant:" + tenantId)
        );

        // All three roles must have a role#tenant -> tenant tuple
        var roleIds = tuples.stream()
                .filter(t -> t.relation().equals("tenant") && t.object().startsWith("role:"))
                .map(t -> t.object().substring("role:".length()))
                .toList();
        assertThat(roleIds).hasSize(3);

        // Admin role: full set of management permissions
        // Tupel-Format: (role:{id}#assignee, relation, object)
        var adminRoleObjects = tuples.stream()
                .filter(t -> t.user().contains("#assignee") && t.user().startsWith("role:"))
                .map(RelationshipTuple::object)
                .toList();
        assertThat(adminRoleObjects).contains(
                "user_management:" + tenantId,
                "role_management:" + tenantId,
                "tenant_config:" + tenantId
        );

        // 7 admin role permission tuples (inviter no longer a separate role)
        var roleAssigneeTupleCount = tuples.stream()
                .filter(t -> t.user().contains("#assignee") && t.user().startsWith("role:"))
                .count();
        assertThat(roleAssigneeTupleCount).isGreaterThanOrEqualTo(7);
    }

    @Test
    void shouldPersistFailedStatusOnS3Error() {
        var tenantId = UUID.randomUUID();
        var event = new TenantCreatedEvent(tenantId, "acme", "admin@acme.com", "Max", "Muster");
        var initialStatus = ProvisioningStatus.initial("admin@acme.com", "Max", "Muster");

        when(statusPersister.loadSnapshot(tenantId))
                .thenReturn(new ProvisioningStatusPersister.ProvisioningSnapshot("Acme Corp", initialStatus));
        doThrow(new RuntimeException("S3 error")).when(fileStorageService).createBucket(any());

        listener.onTenantCreated(event);

        // 1 persist call for failure
        var statusCaptor = ArgumentCaptor.forClass(ProvisioningStatus.class);
        var completedCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(statusPersister).persistStatus(eq(tenantId), statusCaptor.capture(),
                completedCaptor.capture(), any());

        assertThat(completedCaptor.getValue()).isFalse();
        var status = statusCaptor.getValue();
        assertThat(status.steps().get(0).status()).isEqualTo(ProvisioningStepStatus.FAILED);
        assertThat(status.steps().get(0).step()).isEqualTo(ProvisioningStepType.S3_BUCKET);
    }

    @Test
    void shouldSkipCompletedSteps() {
        var tenantId = UUID.randomUUID();
        var event = new TenantCreatedEvent(tenantId, "acme", "admin@acme.com", "Max", "Muster");

        var steps = java.util.List.of(
                io.webcontify.shared.model.ProvisioningStep.pending(ProvisioningStepType.S3_BUCKET).completed(),
                io.webcontify.shared.model.ProvisioningStep.pending(ProvisioningStepType.KEYCLOAK_REALM),
                io.webcontify.shared.model.ProvisioningStep.pending(ProvisioningStepType.OPENFGA_STORE),
                io.webcontify.shared.model.ProvisioningStep.pending(ProvisioningStepType.INITIAL_ADMIN)
        );
        var partialStatus = new ProvisioningStatus(steps, "admin@acme.com", "Max", "Muster");

        when(statusPersister.loadSnapshot(tenantId))
                .thenReturn(new ProvisioningStatusPersister.ProvisioningSnapshot("Acme Corp", partialStatus));
        when(keycloakAdminService.createRealm(any())).thenReturn(new CreateRealmResponse("tenant-acme"));
        when(keycloakAdminService.createUser(any())).thenReturn(new CreateKeycloakUserResponse(UUID.randomUUID()));
        when(openFgaService.createStore(any())).thenReturn(new CreateStoreResponse("store-456"));

        listener.onTenantCreated(event);

        verifyNoInteractions(fileStorageService);
        verify(keycloakAdminService).createRealm(any());
    }

    @Test
    void shouldSetThemeAndBrandingAttributesOnRealmCreation() {
        var tenantId = UUID.randomUUID();
        var event = new TenantCreatedEvent(tenantId, "acme", "admin@acme.com", "Max", "Muster");
        var initialStatus = ProvisioningStatus.initial("admin@acme.com", "Max", "Muster");

        when(statusPersister.loadSnapshot(tenantId))
                .thenReturn(new ProvisioningStatusPersister.ProvisioningSnapshot("Acme Corp", initialStatus));
        when(keycloakAdminService.createRealm(any())).thenReturn(new CreateRealmResponse("tenant-acme"));
        when(keycloakAdminService.createUser(any())).thenReturn(new CreateKeycloakUserResponse(UUID.randomUUID()));
        when(openFgaService.createStore(any())).thenReturn(new CreateStoreResponse("store-123"));

        listener.onTenantCreated(event);

        var captor = ArgumentCaptor.forClass(CreateRealmRequest.class);
        verify(keycloakAdminService).createRealm(captor.capture());

        var request = captor.getValue();
        assertThat(request.loginTheme()).isEqualTo("webcontify");
        assertThat(request.emailTheme()).isEqualTo("webcontify");
        assertThat(request.initialAttributes()).containsEntry("brandTenantName", "Acme Corp");
        assertThat(request.initialAttributes()).containsEntry("brandPrimaryColor", "#6366F1");
        assertThat(request.initialAttributes()).containsEntry("brandSecondaryColor", "#10B981");
    }
}
