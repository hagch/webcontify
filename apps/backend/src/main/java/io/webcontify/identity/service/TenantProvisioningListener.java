package io.webcontify.identity.service;

import io.webcontify.identity.config.PlatformSmtpProperties;
import io.webcontify.identity.model.*;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.model.*;
import io.webcontify.tenant.event.TenantCreatedEvent;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TenantProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningListener.class);

    private final TenantRepository tenantRepository;
    private final FileStorageService fileStorageService;
    private final KeycloakAdminService keycloakAdminService;
    private final OpenFgaService openFgaService;
    private final PlatformSmtpProperties platformSmtpProperties;

    public TenantProvisioningListener(
            TenantRepository tenantRepository,
            FileStorageService fileStorageService,
            KeycloakAdminService keycloakAdminService,
            OpenFgaService openFgaService,
            PlatformSmtpProperties platformSmtpProperties) {
        this.tenantRepository = tenantRepository;
        this.fileStorageService = fileStorageService;
        this.keycloakAdminService = keycloakAdminService;
        this.openFgaService = openFgaService;
        this.platformSmtpProperties = platformSmtpProperties;
    }

    @ApplicationModuleListener
    public void onTenantCreated(TenantCreatedEvent event) {
        log.info("Starting provisioning for tenant slug={}", event.slug());

        var entity = tenantRepository.findById(event.tenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + event.tenantId()));

        var initialStatus = ProvisioningStatus.fromJson(entity.getProvisioningStatus());
        var steps = new ArrayList<>(initialStatus.steps());

        // Step 0: S3 Bucket
        if (!executeStep(steps, 0, ProvisioningStepType.S3_BUCKET, () ->
                fileStorageService.createBucket("tenant-" + event.slug()))) {
            persistStatus(entity, steps, initialStatus, false);
            return;
        }

        // Step 1: Keycloak Realm
        if (!executeStep(steps, 1, ProvisioningStepType.KEYCLOAK_REALM, () -> {
            var smtpConfig = new SmtpConfig(
                    platformSmtpProperties.host(),
                    platformSmtpProperties.port(),
                    platformSmtpProperties.username(),
                    platformSmtpProperties.password(),
                    platformSmtpProperties.starttls());
            var response = keycloakAdminService.createRealm(new CreateRealmRequest(event.slug(), smtpConfig));
            entity.setKeycloakRealmName(response.realmName());
        })) {
            persistStatus(entity, steps, initialStatus, false);
            return;
        }

        // Step 2: OpenFGA Store
        if (!executeStep(steps, 2, ProvisioningStepType.OPENFGA_STORE, () -> {
            var response = openFgaService.createStore(new CreateStoreRequest(event.slug()));
            entity.setOpenfgaStoreId(response.storeId());
        })) {
            persistStatus(entity, steps, initialStatus, false);
            return;
        }

        // Step 3: Initial Admin
        if (!executeStep(steps, 3, ProvisioningStepType.INITIAL_ADMIN, () -> {
            var realmName = entity.getKeycloakRealmName();
            var storeId = entity.getOpenfgaStoreId();

            var userResponse = keycloakAdminService.createUser(
                    new CreateKeycloakUserRequest(realmName, event.adminEmail(), event.adminFirstName(), event.adminLastName()));

            openFgaService.write(new OpenFgaWriteRequest(storeId, List.of(
                    new RelationshipTuple(
                            "user:" + userResponse.keycloakUserId(),
                            "admin",
                            "tenant:" + event.tenantId()))));

            keycloakAdminService.sendInviteEmail(new SendInviteEmailRequest(realmName, userResponse.keycloakUserId()));
        })) {
            persistStatus(entity, steps, initialStatus, false);
            return;
        }

        persistStatus(entity, steps, initialStatus, true);
        log.info("Provisioning completed for tenant slug={}", event.slug());
    }

    private boolean executeStep(List<ProvisioningStep> steps, int index,
                                ProvisioningStepType expectedType, Runnable action) {
        var step = steps.get(index);
        if (step.status() == ProvisioningStepStatus.COMPLETED) {
            return true;
        }
        if (step.step() != expectedType) {
            throw new IllegalStateException("Step mismatch at index " + index + ": expected " + expectedType + " but got " + step.step());
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

    private void persistStatus(TenantEntity entity, List<ProvisioningStep> steps,
                               ProvisioningStatus originalStatus, boolean completed) {
        var finalStatus = new ProvisioningStatus(
                steps,
                originalStatus.adminEmail(),
                originalStatus.adminFirstName(),
                originalStatus.adminLastName());
        entity.setProvisioningStatus(finalStatus.toJson());
        if (completed) {
            entity.setStatus(TenantStatus.ACTIVE.name());
        }
        tenantRepository.save(entity);
    }
}
