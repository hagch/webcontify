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

    public record TenantConfigWithInfo(String name, String slug, TenantConfig config) {}

    @Transactional(readOnly = true)
    public TenantConfigWithInfo getConfig(UUID tenantId) {
        TenantEntity entity = findTenantOrThrow(tenantId);
        TenantConfig config = entity.getConfig();
        if (config == null) {
            config = new TenantConfig(null, null, null);
        }
        return new TenantConfigWithInfo(entity.getName(), entity.getSlug(), stripPassword(config));
    }

    public TenantConfigWithInfo updateBranding(UUID tenantId, BrandingConfig branding) {
        TenantEntity entity = findTenantOrThrow(tenantId);
        TenantConfig existing = getOrEmpty(entity.getConfig());

        TenantConfig updated = new TenantConfig(branding, existing.email(), existing.domain());
        entity.setConfig(updated);
        tenantRepository.save(entity);

        String logoPublicUrl = resolvePublicUrl(tenantId, branding.logoStorageKey());
        String faviconPublicUrl = resolvePublicUrl(tenantId, branding.faviconStorageKey());
        eventPublisher.publishEvent(new TenantBrandingUpdatedEvent(
                entity.getId(),
                entity.getName(),
                entity.getKeycloakRealmName(),
                branding.primaryColor(),
                branding.secondaryColor(),
                branding.accentColor(),
                branding.dangerColor(),
                branding.successColor(),
                branding.warningColor(),
                branding.fontFamily(),
                branding.borderRadius(),
                logoPublicUrl,
                faviconPublicUrl));

        return new TenantConfigWithInfo(entity.getName(), entity.getSlug(), stripPassword(updated));
    }

    private String resolvePublicUrl(UUID tenantId, String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        return tenantFileService.getFileUrl(tenantId, storageKey);
    }

    public TenantConfigWithInfo updateEmail(UUID tenantId, EmailConfig email) {
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

        return new TenantConfigWithInfo(entity.getName(), entity.getSlug(), stripPassword(updated));
    }

    public TenantConfigWithInfo updateDomain(UUID tenantId, DomainConfig domain) {
        TenantEntity entity = findTenantOrThrow(tenantId);
        TenantConfig existing = getOrEmpty(entity.getConfig());

        TenantConfig updated = new TenantConfig(existing.branding(), existing.email(), domain);
        entity.setConfig(updated);
        tenantRepository.save(entity);

        return new TenantConfigWithInfo(entity.getName(), entity.getSlug(), stripPassword(updated));
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
