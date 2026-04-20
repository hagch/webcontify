package io.webcontify.tenant.service;

import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.ProvisioningStatus;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.shared.util.SlugGenerator;
import io.webcontify.shared.util.UuidGenerator;
import io.webcontify.tenant.event.TenantCreatedEvent;
import io.webcontify.tenant.event.TenantDeletedEvent;
import io.webcontify.tenant.mapper.TenantMapper;
import io.webcontify.tenant.model.Tenant;
import io.webcontify.tenant.model.TenantInfrastructureInfo;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final SlugGenerator slugGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public TenantService(
            TenantRepository tenantRepository,
            TenantMapper tenantMapper,
            SlugGenerator slugGenerator,
            ApplicationEventPublisher eventPublisher) {
        this.tenantRepository = tenantRepository;
        this.tenantMapper = tenantMapper;
        this.slugGenerator = slugGenerator;
        this.eventPublisher = eventPublisher;
    }

    public Tenant create(Tenant tenant, String adminEmail, String adminFirstName, String adminLastName) {
        checkNameUniqueness(tenant.getName(), null);

        String slug = slugGenerator.generateUnique(
                tenant.getName(),
                s -> tenantRepository.findBySlugAndAuditInfoDeletedAtIsNull(s).isPresent());
        tenant.setSlug(slug);

        tenant.setId(UuidGenerator.generate());
        tenant.setStatus(TenantStatus.PROVISIONING);
        tenant.setProvisioningStatus(
                ProvisioningStatus.initial(adminEmail, adminFirstName, adminLastName).toJson());

        TenantEntity entity = tenantMapper.toEntity(tenant);
        entity = tenantRepository.save(entity);
        Tenant saved = tenantMapper.toDomain(entity);

        eventPublisher.publishEvent(new TenantCreatedEvent(
                saved.getId(),
                saved.getSlug(),
                adminEmail,
                adminFirstName,
                adminLastName));

        return saved;
    }

    @Transactional(readOnly = true)
    public TenantInfrastructureInfo getInfrastructureInfo(UUID id) {
        TenantEntity entity = tenantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("tenant.not_found"));
        return new TenantInfrastructureInfo(entity.getOpenfgaStoreId(), entity.getKeycloakRealmName());
    }

    @Transactional(readOnly = true)
    public boolean isNameAvailable(String name) {
        return !tenantRepository.existsByNameAndAuditInfoDeletedAtIsNull(name);
    }

    @Transactional(readOnly = true)
    public Page<Tenant> list(Pageable pageable, TenantStatus status) {
        Page<TenantEntity> page;
        if (status != null) {
            page =
                    tenantRepository.findByStatusAndAuditInfoDeletedAtIsNull(
                            status.name(), pageable);
        } else {
            page = tenantRepository.findByAuditInfoDeletedAtIsNull(pageable);
        }
        return page.map(tenantMapper::toDomain);
    }

    @Transactional(readOnly = true)
    public Tenant getById(UUID id) {
        TenantEntity entity =
                tenantRepository
                        .findByIdAndAuditInfoDeletedAtIsNull(id)
                        .orElseThrow(() -> new NotFoundException("tenant.not_found"));
        return tenantMapper.toDomain(entity);
    }

    public Tenant update(UUID id, Tenant update) {
        TenantEntity entity =
                tenantRepository
                        .findByIdAndAuditInfoDeletedAtIsNull(id)
                        .orElseThrow(() -> new NotFoundException("tenant.not_found"));

        checkSlugUniqueness(update.getSlug(), id);

        tenantMapper.updateEntity(entity, update);
        entity = tenantRepository.save(entity);
        return tenantMapper.toDomain(entity);
    }

    public Tenant updateStatus(UUID id, TenantStatus status) {
        TenantEntity entity =
                tenantRepository
                        .findByIdAndAuditInfoDeletedAtIsNull(id)
                        .orElseThrow(() -> new NotFoundException("tenant.not_found"));

        entity.setStatus(status.name());
        entity = tenantRepository.save(entity);
        return tenantMapper.toDomain(entity);
    }

    public void delete(UUID id) {
        TenantEntity entity =
                tenantRepository
                        .findByIdAndAuditInfoDeletedAtIsNull(id)
                        .orElseThrow(() -> new NotFoundException("tenant.not_found"));

        entity.getAuditInfo().setDeletedAt(OffsetDateTime.now());
        entity.getAuditInfo().setDeletedBy(SystemUser.ID);
        tenantRepository.save(entity);

        eventPublisher.publishEvent(new TenantDeletedEvent(entity.getId(), entity.getSlug()));
    }

    private void checkNameUniqueness(String name, UUID excludeId) {
        if (tenantRepository.existsByNameAndAuditInfoDeletedAtIsNull(name)) {
            throw new ConflictException("tenant.name_already_exists");
        }
    }

    private void checkSlugUniqueness(String slug, UUID excludeId) {
        tenantRepository
                .findBySlugAndAuditInfoDeletedAtIsNull(slug)
                .ifPresent(
                        existing -> {
                            if (excludeId == null || !existing.getId().equals(excludeId)) {
                                throw new ConflictException("tenant.slug_already_exists");
                            }
                        });
    }
}
