package io.webcontify.identity.service;

import io.webcontify.identity.config.PlatformSmtpProperties;
import io.webcontify.identity.model.*;
import io.webcontify.identity.repository.TenantRoleRepository;
import io.webcontify.identity.repository.TenantUserRepository;
import io.webcontify.identity.repository.entity.TenantRoleEntity;
import io.webcontify.identity.repository.entity.TenantUserEntity;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.model.*;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.tenant.event.TenantCreatedEvent;
import io.webcontify.tenant.service.TenantSchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class TenantProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningListener.class);

    private final ProvisioningStatusPersister statusPersister;
    private final FileStorageService fileStorageService;
    private final KeycloakAdminService keycloakAdminService;
    private final OpenFgaService openFgaService;
    private final PlatformSmtpProperties platformSmtpProperties;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRoleRepository tenantRoleRepository;
    private final TenantSchemaService tenantSchemaService;

    public TenantProvisioningListener(
            ProvisioningStatusPersister statusPersister,
            FileStorageService fileStorageService,
            KeycloakAdminService keycloakAdminService,
            OpenFgaService openFgaService,
            PlatformSmtpProperties platformSmtpProperties,
            TenantUserRepository tenantUserRepository,
            TenantRoleRepository tenantRoleRepository,
            TenantSchemaService tenantSchemaService) {
        this.statusPersister = statusPersister;
        this.fileStorageService = fileStorageService;
        this.keycloakAdminService = keycloakAdminService;
        this.openFgaService = openFgaService;
        this.platformSmtpProperties = platformSmtpProperties;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRoleRepository = tenantRoleRepository;
        this.tenantSchemaService = tenantSchemaService;
    }

    @ApplicationModuleListener
    public void onTenantCreated(TenantCreatedEvent event) {
        log.info("Starting provisioning for tenant slug={}", event.slug());

        var snapshot = statusPersister.loadSnapshot(event.tenantId());
        var initialStatus = snapshot.status().migrateIfNeeded();
        var steps = new ArrayList<>(initialStatus.steps());

        String realmName = null;
        String storeId = null;

        // Step 0: S3 Bucket
        if (!executeStep(steps, 0, ProvisioningStepType.S3_BUCKET, () ->
                fileStorageService.createBucket("tenant-" + event.slug()))) {
            persistAndReturn(event, steps, initialStatus, false, null);
            return;
        }
        persistAndReturn(event, steps, initialStatus, false, null);

        // Step 1: Keycloak Realm
        var realmHolder = new String[1];
        if (!executeStep(steps, 1, ProvisioningStepType.KEYCLOAK_REALM, () -> {
            var smtpConfig = new SmtpConfig(
                    platformSmtpProperties.host(),
                    platformSmtpProperties.port(),
                    platformSmtpProperties.from(),
                    platformSmtpProperties.username(),
                    platformSmtpProperties.password(),
                    platformSmtpProperties.starttls());
            var response = keycloakAdminService.createRealm(new CreateRealmRequest(
                    event.slug(),
                    event.tenantId(),
                    snapshot.tenantName(),
                    smtpConfig,
                    "webcontify",
                    "webcontify",
                    Map.of(
                            "brandTenantName", snapshot.tenantName(),
                            "brandPrimaryColor", "#6366F1",
                            "brandSecondaryColor", "#10B981"
                    )
            ));
            realmHolder[0] = response.realmName();
        })) {
            persistAndReturn(event, steps, initialStatus, false, null);
            return;
        }
        realmName = realmHolder[0];
        final String finalRealmName = realmName;
        persistAndReturn(event, steps, initialStatus, false,
                u -> u.setKeycloakRealmName(finalRealmName));

        // Step 2: OpenFGA Store
        var storeHolder = new String[1];
        if (!executeStep(steps, 2, ProvisioningStepType.OPENFGA_STORE, () -> {
            var response = openFgaService.createStore(new CreateStoreRequest(event.slug()));
            storeHolder[0] = response.storeId();
        })) {
            persistAndReturn(event, steps, initialStatus, false, null);
            return;
        }
        storeId = storeHolder[0];
        final String finalStoreId = storeId;
        persistAndReturn(event, steps, initialStatus, false,
                u -> u.setOpenfgaStoreId(finalStoreId));

        // Step 3: Tenant Schema
        if (!executeStep(steps, 3, ProvisioningStepType.TENANT_SCHEMA, () ->
                tenantSchemaService.createSchema(event.slug()))) {
            persistAndReturn(event, steps, initialStatus, false, null);
            return;
        }
        persistAndReturn(event, steps, initialStatus, false, null);

        // Step 4: Initial Admin
        final String adminRealmName = realmName;
        final String adminStoreId = storeId;
        if (!executeStep(steps, 4, ProvisioningStepType.INITIAL_ADMIN, () -> {
            var userResponse = keycloakAdminService.createUser(
                    new CreateKeycloakUserRequest(adminRealmName, event.adminEmail(),
                            event.adminFirstName(), event.adminLastName()));
            UUID keycloakUserId = userResponse.keycloakUserId();

            setProvisioningSecurityContext(event.tenantId());
            TenantRoleEntity adminRole;
            TenantRoleEntity editorRole;
            TenantRoleEntity viewerRole;
            try {
                adminRole = createSystemRole(event.tenantId(), "Admin");
                editorRole = createSystemRole(event.tenantId(), "Editor");
                viewerRole = createSystemRole(event.tenantId(), "Viewer");

                var userEntity = new TenantUserEntity();
                userEntity.setId(keycloakUserId);
                userEntity.setTenantId(event.tenantId());
                userEntity.setEmail(event.adminEmail());
                userEntity.setFirstName(event.adminFirstName());
                userEntity.setLastName(event.adminLastName());
                userEntity.setStatus(UserStatus.INVITED);
                tenantUserRepository.save(userEntity);
            } finally {
                clearProvisioningSecurityContext();
            }

            String tid = event.tenantId().toString();
            String adminRoleId = adminRole.getId().toString();
            String editorRoleId = editorRole.getId().toString();
            String viewerRoleId = viewerRole.getId().toString();

            var tuples = new ArrayList<RelationshipTuple>();

            // Singleton resources linked to tenant
            tuples.add(new RelationshipTuple("tenant:" + tid, "tenant", "user_management:" + tid));
            tuples.add(new RelationshipTuple("tenant:" + tid, "tenant", "role_management:" + tid));
            tuples.add(new RelationshipTuple("tenant:" + tid, "tenant", "tenant_config:" + tid));
            tuples.add(new RelationshipTuple("tenant:" + tid, "tenant", "app_management:" + tid));

            // Role-tenant membership for all 3 roles
            tuples.add(new RelationshipTuple("tenant:" + tid, "tenant", "role:" + adminRoleId));
            tuples.add(new RelationshipTuple("tenant:" + tid, "tenant", "role:" + editorRoleId));
            tuples.add(new RelationshipTuple("tenant:" + tid, "tenant", "role:" + viewerRoleId));

            // Admin role: full management permissions
            tuples.add(new RelationshipTuple("role:" + adminRoleId + "#assignee", "inviter", "user_management:" + tid));
            tuples.add(new RelationshipTuple("role:" + adminRoleId + "#assignee", "editor",  "user_management:" + tid));
            tuples.add(new RelationshipTuple("role:" + adminRoleId + "#assignee", "deleter", "user_management:" + tid));
            tuples.add(new RelationshipTuple("role:" + adminRoleId + "#assignee", "creator", "role_management:" + tid));
            tuples.add(new RelationshipTuple("role:" + adminRoleId + "#assignee", "editor",  "role_management:" + tid));
            tuples.add(new RelationshipTuple("role:" + adminRoleId + "#assignee", "deleter", "role_management:" + tid));
            tuples.add(new RelationshipTuple("role:" + adminRoleId + "#assignee", "editor",  "tenant_config:" + tid));
            tuples.add(new RelationshipTuple("role:" + adminRoleId + "#assignee", "viewer", "app_management:" + tid));
            tuples.add(new RelationshipTuple("role:" + adminRoleId + "#assignee", "editor", "app_management:" + tid));
            tuples.add(new RelationshipTuple("role:" + adminRoleId + "#assignee", "admin",  "app_management:" + tid));

            // tenant#admin tuple for computed usersets (must remain)
            tuples.add(new RelationshipTuple("user:" + keycloakUserId, "admin", "tenant:" + tid));

            // Admin user gets Admin role
            tuples.add(new RelationshipTuple("user:" + keycloakUserId, "assignee", "role:" + adminRoleId));

            openFgaService.write(new OpenFgaWriteRequest(adminStoreId, tuples));

            keycloakAdminService.sendInviteEmail(
                    new SendInviteEmailRequest(adminRealmName, keycloakUserId, "http://" + event.slug() + ".localhost"));
        })) {
            persistAndReturn(event, steps, initialStatus, false, null);
            return;
        }

        persistAndReturn(event, steps, initialStatus, true, null);
        log.info("Provisioning completed for tenant slug={}", event.slug());
    }

    private boolean executeStep(List<ProvisioningStep> steps, int index,
                                ProvisioningStepType expectedType, Runnable action) {
        var step = steps.get(index);
        if (step.status() == ProvisioningStepStatus.COMPLETED) {
            return true;
        }
        if (step.step() != expectedType) {
            throw new IllegalStateException(
                    "Step mismatch at index " + index + ": expected " + expectedType
                            + " but got " + step.step());
        }
        try {
            action.run();
            steps.set(index, step.completed());
            return true;
        } catch (Exception e) {
            log.error("Provisioning step {} failed for index={}", expectedType, index, e);
            steps.set(index, step.failed(e.getMessage()));
            return false;
        }
    }

    private void persistAndReturn(TenantCreatedEvent event, List<ProvisioningStep> steps,
                                  ProvisioningStatus originalStatus, boolean completed,
                                  java.util.function.Consumer<ProvisioningStatusPersister.EntityUpdater> updater) {
        var status = new ProvisioningStatus(
                steps,
                originalStatus.adminEmail(),
                originalStatus.adminFirstName(),
                originalStatus.adminLastName());
        statusPersister.persistStatus(event.tenantId(), status, completed, updater);
    }

    private void setProvisioningSecurityContext(UUID tenantId) {
        var tenantInfo = new TenantInformation(tenantId, false);
        var auth = new TenantAwareAuthentication(
                tenantInfo, SystemUser.ID.toString(), List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void clearProvisioningSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private TenantRoleEntity createSystemRole(UUID tenantId, String name) {
        var role = new TenantRoleEntity();
        role.setId(com.fasterxml.uuid.Generators.timeBasedEpochGenerator().generate());
        role.setTenantId(tenantId);
        role.setName(name);
        role.setSystemRole(true);
        return tenantRoleRepository.save(role);
    }
}
