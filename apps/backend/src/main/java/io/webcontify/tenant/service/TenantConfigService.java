package io.webcontify.tenant.service;

import io.webcontify.shared.encryption.EncryptionService;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.tenant.model.TenantConfig;
import io.webcontify.tenant.model.TenantConfig.BrandingConfig;
import io.webcontify.tenant.model.TenantConfig.DomainConfig;
import io.webcontify.tenant.model.TenantConfig.EmailConfig;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantConfigService {

    private final TenantRepository tenantRepository;
    private final EncryptionService encryptionService;

    public TenantConfigService(TenantRepository tenantRepository, EncryptionService encryptionService) {
        this.tenantRepository = tenantRepository;
        this.encryptionService = encryptionService;
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

        return stripPassword(updated);
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
