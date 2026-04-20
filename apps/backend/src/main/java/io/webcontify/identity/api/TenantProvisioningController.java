package io.webcontify.identity.api;

import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.ProvisioningStatus;
import io.webcontify.tenant.event.TenantCreatedEvent;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.repository.TenantRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/tenants")
public class TenantProvisioningController {

    private final TenantRepository tenantRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TenantProvisioningController(TenantRepository tenantRepository,
                                        ApplicationEventPublisher eventPublisher) {
        this.tenantRepository = tenantRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/{tenantId}/retry-provisioning")
    public ResponseEntity<Void> retryProvisioning(@PathVariable UUID tenantId) {
        var entity = tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId)
                .orElseThrow(() -> new NotFoundException("tenant.not_found"));

        if (!TenantStatus.PROVISIONING.name().equals(entity.getStatus())) {
            throw new ConflictException("tenant.not_in_provisioning_state");
        }

        var status = ProvisioningStatus.fromJson(entity.getProvisioningStatus());

        eventPublisher.publishEvent(new TenantCreatedEvent(
                entity.getId(), entity.getSlug(),
                status.adminEmail(), status.adminFirstName(), status.adminLastName()
        ));

        return ResponseEntity.accepted().build();
    }
}
