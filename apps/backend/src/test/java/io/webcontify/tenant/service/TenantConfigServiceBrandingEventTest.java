package io.webcontify.tenant.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.webcontify.shared.encryption.EncryptionService;
import io.webcontify.tenant.event.TenantBrandingUpdatedEvent;
import io.webcontify.tenant.model.TenantConfig;
import io.webcontify.tenant.model.TenantConfig.BrandingConfig;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TenantConfigServiceBrandingEventTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TenantFileService tenantFileService;

    private TenantConfigService tenantConfigService;

    @BeforeEach
    void setUp() {
        tenantConfigService = new TenantConfigService(
                tenantRepository, encryptionService, eventPublisher, tenantFileService);
    }

    @Test
    void shouldPublishBrandingUpdatedEventAfterSave() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity entity = createEntity(tenantId);
        entity.setKeycloakRealmName("tenant-acme");
        entity.setConfig(new TenantConfig(
                new BrandingConfig("config/logo.png", null, "#ff0000", "#00ff00"),
                null, null));

        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.of(entity));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tenantFileService.getFileUrl(tenantId, "config/logo.png"))
                .thenReturn("https://s3.example.com/tenant-acme/config/logo.png");

        BrandingConfig newBranding = new BrandingConfig("config/logo.png", null, "#112233", "#445566");
        tenantConfigService.updateBranding(tenantId, newBranding);

        ArgumentCaptor<TenantBrandingUpdatedEvent> captor =
                ArgumentCaptor.forClass(TenantBrandingUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        TenantBrandingUpdatedEvent event = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(event.tenantId()).isEqualTo(tenantId);
        org.assertj.core.api.Assertions.assertThat(event.tenantName()).isEqualTo("Acme Corp");
        org.assertj.core.api.Assertions.assertThat(event.keycloakRealmName()).isEqualTo("tenant-acme");
        org.assertj.core.api.Assertions.assertThat(event.primaryColor()).isEqualTo("#112233");
        org.assertj.core.api.Assertions.assertThat(event.secondaryColor()).isEqualTo("#445566");
        org.assertj.core.api.Assertions.assertThat(event.logoPublicUrl())
                .isEqualTo("https://s3.example.com/tenant-acme/config/logo.png");
    }

    @Test
    void shouldPublishEventWithNullLogoUrlWhenNoLogoStorageKey() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity entity = createEntity(tenantId);
        entity.setKeycloakRealmName("tenant-acme");
        entity.setConfig(null);

        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.of(entity));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BrandingConfig newBranding = new BrandingConfig(null, null, "#112233", null);
        tenantConfigService.updateBranding(tenantId, newBranding);

        ArgumentCaptor<TenantBrandingUpdatedEvent> captor =
                ArgumentCaptor.forClass(TenantBrandingUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        TenantBrandingUpdatedEvent event = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(event.logoPublicUrl()).isNull();
        verifyNoInteractions(tenantFileService);
    }

    @Test
    void shouldPublishEventAfterSaveNotBefore() {
        UUID tenantId = UUID.randomUUID();
        TenantEntity entity = createEntity(tenantId);
        entity.setKeycloakRealmName("tenant-acme");
        entity.setConfig(null);

        when(tenantRepository.findByIdAndAuditInfoDeletedAtIsNull(tenantId))
                .thenReturn(Optional.of(entity));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BrandingConfig newBranding = new BrandingConfig(null, null, "#aabbcc", null);
        tenantConfigService.updateBranding(tenantId, newBranding);

        var inOrder = inOrder(tenantRepository, eventPublisher);
        inOrder.verify(tenantRepository).save(any());
        inOrder.verify(eventPublisher).publishEvent(any(TenantBrandingUpdatedEvent.class));
    }

    private TenantEntity createEntity(UUID tenantId) {
        TenantEntity entity = new TenantEntity();
        entity.setId(tenantId);
        entity.setName("Acme Corp");
        entity.setSlug("acme");
        entity.setStatus("ACTIVE");
        return entity;
    }
}
