package io.webcontify.tenant.service;

import io.webcontify.shared.encryption.EncryptionService;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.tenant.event.TenantBrandingUpdatedEvent;
import io.webcontify.tenant.model.TenantConfig;
import io.webcontify.tenant.model.TenantConfig.BrandingConfig;
import io.webcontify.tenant.model.TenantConfig.DomainConfig;
import io.webcontify.tenant.model.TenantConfig.EmailConfig;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantConfigService {

    private final TenantRepository tenantRepository;
    private final EncryptionService encryptionService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantFileService tenantFileService;

    public TenantConfigService(
            TenantRepository tenantRepository,
            EncryptionService encryptionService,
            ApplicationEventPublisher eventPublisher,
            TenantFileService tenantFileService) {
        this.tenantRepository = tenantRepository;
        this.encryptionService = encryptionService;
        this.eventPublisher = eventPublisher;
        this.tenantFileService = tenantFileService;
    }

    @Transactional(readOnly = true)
    public TenantConfig getConfig(UUID tenantId) {
        TenantEntity entity = findTenantOrThrow(tenantId);
        TenantConfig config = entity.getConfig();
        if (config == null) {
            return new TenantConfig(null, null, null);
        }
        return stripPassword(config);
    }

    public TenantConfig updateBranding(UUID tenantId, BrandingConfig branding) {
        TenantEntity entity = findTenantOrThrow(tenantId);
        TenantConfig existing = getOrEmpty(entity.getConfig());

        TenantConfig updated = new TenantConfig(branding, existing.email(), existing.domain());
        entity.setConfig(updated);
        tenantRepository.save(entity);

        String logoPublicUrl = resolveLogoPublicUrl(tenantId, branding);
        eventPublisher.publishEvent(new TenantBrandingUpdatedEvent(
                entity.getId(),
                entity.getName(),
                entity.getKeycloakRealmName(),
                branding.primaryColor(),
                branding.secondaryColor(),
                logoPublicUrl));

        return stripPassword(updated);
    }

    private String resolveLogoPublicUrl(UUID tenantId, BrandingConfig branding) {
        if (branding.logoStorageKey() == null || branding.logoStorageKey().isBlank()) {
            return null;
        }
        return tenantFileService.getFileUrl(tenantId, branding.logoStorageKey());
    }

    public TenantConfig updateEmail(UUID tenantId, EmailConfig email) {
        TenantEntity entity = findTenantOrThrow(tenantId);
        TenantConfig existing = getOrEmpty(entity.getConfig());

        String encryptedPassword;
        if (email.smtpPassword() != null && !email.smtpPassword().isBlank()) {
            encryptedPassword = encryptionService.encrypt(email.smtpPassword());
        } else {
            encryptedPassword = existing.email() != null ? existing.email().smtpPassword() : null;
        }

        EmailConfig emailWithEncryptedPassword = new EmailConfig(
                email.smtpHost(),
                email.smtpPort(),
                email.smtpUser(),
                encryptedPassword,
                email.fromAddress(),
                email.fromName());

        TenantConfig updated = new TenantConfig(existing.branding(), emailWithEncryptedPassword, existing.domain());
        entity.setConfig(updated);
        tenantRepository.save(entity);

        return stripPassword(updated);
    }

    public TenantConfig updateDomain(UUID tenantId, DomainConfig domain) {
        TenantEntity entity = findTenantOrThrow(tenantId);
        TenantConfig existing = getOrEmpty(entity.getConfig());

        TenantConfig updated = new TenantConfig(existing.branding(), existing.email(), domain);
        entity.setConfig(updated);
        tenantRepository.save(entity);

        return stripPassword(updated);
    }

    private TenantEntity findTenantOrThrow(UUID tenantId) {
        return tenantRepository
                .findByIdAndAuditInfoDeletedAtIsNull(tenantId)
                .orElseThrow(() -> new NotFoundException("tenant.not_found"));
    }

    private TenantConfig getOrEmpty(TenantConfig config) {
        return config != null ? config : new TenantConfig(null, null, null);
    }

    private TenantConfig stripPassword(TenantConfig config) {
        EmailConfig email = config.email();
        if (email == null) {
            return config;
        }
        EmailConfig sanitized = new EmailConfig(
                email.smtpHost(),
                email.smtpPort(),
                email.smtpUser(),
                null,
                email.fromAddress(),
                email.fromName());
        return new TenantConfig(config.branding(), sanitized, config.domain());
    }
}
