package io.webcontify.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.webcontify.shared.encryption.EncryptionService;
import io.webcontify.shared.exception.NotFoundException;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.tenant.model.TenantConfig;
import io.webcontify.tenant.model.TenantConfig.BrandingConfig;
import io.webcontify.tenant.model.TenantConfig.DomainConfig;
import io.webcontify.tenant.model.TenantConfig.EmailConfig;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantConfigServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private TenantFileService tenantFileService;

    private TenantConfigService tenantConfigService;

    @BeforeEach
    void setUp() {
        tenantConfigService = new TenantConfigService(
                tenantRepository, encryptionService, eventPublisher, tenantFileService);
    }

    @Test
    void shouldReturnConfigWithPasswordNull() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity entity = createEntityWithConfig(tenantId);
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.of(entity));

        var result = tenantConfigService.getConfig(tenantId);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Test");
        assertThat(result.slug()).isEqualTo("test");
        assertThat(result.config().branding().primaryColor()).isEqualTo("#ff0000");
        assertThat(result.config().email().smtpHost()).isEqualTo("smtp.example.com");
        assertThat(result.config().email().smtpPassword()).isNull();
        assertThat(result.config().domain().customDomain()).isEqualTo("example.com");
    }

    @Test
    void shouldReturnEmptyConfigWhenConfigIsNull() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity entity = createEntity(tenantId);
        entity.setConfig(null);
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.of(entity));

        var result = tenantConfigService.getConfig(tenantId);

        assertThat(result).isNotNull();
        assertThat(result.config().branding()).isNull();
        assertThat(result.config().email()).isNull();
        assertThat(result.config().domain()).isNull();
    }

    @Test
    void shouldThrowNotFoundWhenTenantDoesNotExist() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantConfigService.getConfig(tenantId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("tenant.not_found");
    }

    @Test
    void shouldMergeBrandingConfigAndKeepOtherSections() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity entity = createEntityWithConfig(tenantId);
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.of(entity));
        when(tenantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BrandingConfig newBranding = new BrandingConfig(null, null, "#00ff00", "#0000ff", null, null, null, null, null, null);

        var result = tenantConfigService.updateBranding(tenantId, newBranding);

        assertThat(result.config().branding().primaryColor()).isEqualTo("#00ff00");
        assertThat(result.config().branding().secondaryColor()).isEqualTo("#0000ff");
        assertThat(result.config().email().smtpHost()).isEqualTo("smtp.example.com");
        assertThat(result.config().email().smtpPassword()).isNull();
        assertThat(result.config().domain().customDomain()).isEqualTo("example.com");
    }

    @Test
    void shouldEncryptSmtpPasswordOnUpdateEmail() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity entity = createEntityWithConfig(tenantId);
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.of(entity));
        when(tenantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(encryptionService.encrypt("my-secret")).thenReturn("encrypted-secret");

        EmailConfig newEmail = new EmailConfig(
                "smtp.new.com", 587, "user", "my-secret", "from@new.com", "New Sender");

        var result = tenantConfigService.updateEmail(tenantId, newEmail);

        assertThat(result.config().email().smtpHost()).isEqualTo("smtp.new.com");
        assertThat(result.config().email().smtpPassword()).isNull();
        verify(encryptionService).encrypt("my-secret");

        verify(tenantRepository).save(argThat(savedEntity -> {
            TenantConfig savedConfig = savedEntity.getConfig();
            return "encrypted-secret".equals(savedConfig.email().smtpPassword());
        }));
    }

    @Test
    void shouldKeepExistingPasswordWhenNewPasswordIsNull() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity entity = createEntityWithConfig(tenantId);
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.of(entity));
        when(tenantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EmailConfig newEmail = new EmailConfig(
                "smtp.new.com", 587, "user", null, "from@new.com", "New Sender");

        tenantConfigService.updateEmail(tenantId, newEmail);

        verify(tenantRepository).save(argThat(savedEntity -> {
            TenantConfig savedConfig = savedEntity.getConfig();
            return "encrypted-pwd".equals(savedConfig.email().smtpPassword());
        }));
    }

    @Test
    void shouldMergeDomainConfigAndKeepOtherSections() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity entity = createEntityWithConfig(tenantId);
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.of(entity));
        when(tenantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DomainConfig newDomain = new DomainConfig("new-domain.com", "PENDING");

        var result = tenantConfigService.updateDomain(tenantId, newDomain);

        assertThat(result.config().domain().customDomain()).isEqualTo("new-domain.com");
        assertThat(result.config().domain().sslStatus()).isEqualTo("PENDING");
        assertThat(result.config().branding().primaryColor()).isEqualTo("#ff0000");
        assertThat(result.config().email().smtpHost()).isEqualTo("smtp.example.com");
        assertThat(result.config().email().smtpPassword()).isNull();
    }

    @Test
    void shouldThrowNotFoundOnUpdateBrandingWhenTenantMissing() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantConfigService.updateBranding(
                        tenantId, new BrandingConfig(null, null, "#ff0000", null, null, null, null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowNotFoundOnUpdateEmailWhenTenantMissing() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantConfigService.updateEmail(
                        tenantId, new EmailConfig("host", 25, null, null, "a@b.com", "Name")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowNotFoundOnUpdateDomainWhenTenantMissing() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantConfigService.updateDomain(
                        tenantId, new DomainConfig("example.com", null)))
                .isInstanceOf(NotFoundException.class);
    }

    private TenantEntity createEntity(UUID tenantId) {
        TenantEntity entity = new TenantEntity();
        entity.setId(tenantId);
        entity.setName("Test");
        entity.setSlug("test");
        entity.setStatus("ACTIVE");

        AuditInfo auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(SystemUser.ID);
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(SystemUser.ID);
        entity.setAuditInfo(auditInfo);

        return entity;
    }

    private TenantEntity createEntityWithConfig(UUID tenantId) {
        TenantEntity entity = createEntity(tenantId);

        BrandingConfig branding = new BrandingConfig("logos/logo.png", "logos/favicon.png", "#ff0000", "#00ff00", null, null, null, null, null, null);
        EmailConfig email = new EmailConfig("smtp.example.com", 587, "user", "encrypted-pwd", "from@example.com", "Test Sender");
        DomainConfig domain = new DomainConfig("example.com", "ACTIVE");

        entity.setConfig(new TenantConfig(branding, email, domain));
        return entity;
    }
}
