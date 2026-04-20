package io.webcontify.tenant.service;

import io.webcontify.shared.exception.ConflictException;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.shared.util.UuidGenerator;
import io.webcontify.tenant.mapper.TenantMapper;
import io.webcontify.tenant.model.Tenant;
import io.webcontify.tenant.model.TenantStatus;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    public TenantService(TenantRepository tenantRepository, TenantMapper tenantMapper) {
        this.tenantRepository = tenantRepository;
        this.tenantMapper = tenantMapper;
    }

    public Tenant create(Tenant tenant) {
        checkSlugUniqueness(tenant.getSlug(), null);

        tenant.setId(UuidGenerator.generate());
        tenant.setStatus(TenantStatus.INACTIVE);

        TenantEntity entity = tenantMapper.toEntity(tenant);
        entity = tenantRepository.save(entity);
        return tenantMapper.toDomain(entity);
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
