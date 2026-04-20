package io.webcontify.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.webcontify.identity.service.KeycloakAdminService;
import io.webcontify.shared.filestorage.FileStorageService;
import io.webcontify.shared.model.AuditInfo;
import io.webcontify.shared.model.SystemUser;
import io.webcontify.shared.security.TestSecurityConfiguration;
import io.webcontify.tenant.event.TenantBrandingUpdatedEvent;
import io.webcontify.tenant.model.TenantConfig.BrandingConfig;
import io.webcontify.tenant.repository.TenantRepository;
import io.webcontify.tenant.repository.entity.TenantEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@RecordApplicationEvents
@Import(TestSecurityConfiguration.class)
class TenantConfigBrandingSyncIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private KeycloakAdminService keycloakAdminService;

    @MockitoBean
    private FileStorageService fileStorageService;

    @Autowired
    private TenantConfigService tenantConfigService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        String shortId = tenantId.toString().substring(0, 8);

        TenantEntity tenant = new TenantEntity();
        tenant.setId(tenantId);
        tenant.setName("Branding Sync Test " + shortId);
        tenant.setSlug("branding-sync-" + shortId);
        tenant.setStatus("ACTIVE");
        tenant.setKeycloakRealmName("tenant-branding-sync-" + shortId);

        AuditInfo auditInfo = new AuditInfo();
        auditInfo.setCreatedAt(OffsetDateTime.now());
        auditInfo.setCreatedBy(SystemUser.ID);
        auditInfo.setUpdatedAt(OffsetDateTime.now());
        auditInfo.setUpdatedBy(SystemUser.ID);
        tenant.setAuditInfo(auditInfo);

        tenantRepository.save(tenant);
    }

    @AfterEach
    void tearDown() {
        tenantRepository.deleteById(tenantId);
    }

    @Test
    void shouldPublishTenantBrandingUpdatedEventAfterBrandingUpdate() {
        BrandingConfig branding = new BrandingConfig(null, null, "#1a2b3c", "#4d5e6f", null, null, null, null, null, null);

        tenantConfigService.updateBranding(tenantId, branding);

        List<TenantBrandingUpdatedEvent> events = applicationEvents
                .stream(TenantBrandingUpdatedEvent.class)
                .toList();

        assertThat(events).hasSize(1);
    }

    @Test
    void shouldPublishEventWithCorrectTenantId() {
        BrandingConfig branding = new BrandingConfig(null, null, "#aabbcc", null, null, null, null, null, null, null);

        tenantConfigService.updateBranding(tenantId, branding);

        TenantBrandingUpdatedEvent event = applicationEvents
                .stream(TenantBrandingUpdatedEvent.class)
                .findFirst()
                .orElseThrow();

        assertThat(event.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void shouldPublishEventWithCorrectTenantNameAndRealmName() {
        String shortId = tenantId.toString().substring(0, 8);
        BrandingConfig branding = new BrandingConfig(null, null, "#aabbcc", null, null, null, null, null, null, null);

        tenantConfigService.updateBranding(tenantId, branding);

        TenantBrandingUpdatedEvent event = applicationEvents
                .stream(TenantBrandingUpdatedEvent.class)
                .findFirst()
                .orElseThrow();

        assertThat(event.tenantName()).isEqualTo("Branding Sync Test " + shortId);
        assertThat(event.keycloakRealmName()).isEqualTo("tenant-branding-sync-" + shortId);
    }

    @Test
    void shouldPublishEventWithCorrectColors() {
        BrandingConfig branding = new BrandingConfig(null, null, "#112233", "#445566", null, null, null, null, null, null);

        tenantConfigService.updateBranding(tenantId, branding);

        TenantBrandingUpdatedEvent event = applicationEvents
                .stream(TenantBrandingUpdatedEvent.class)
                .findFirst()
                .orElseThrow();

        assertThat(event.primaryColor()).isEqualTo("#112233");
        assertThat(event.secondaryColor()).isEqualTo("#445566");
    }

    @Test
    void shouldPublishEventWithNullLogoUrlWhenNoLogoStorageKey() {
        BrandingConfig branding = new BrandingConfig(null, null, "#aabbcc", null, null, null, null, null, null, null);

        tenantConfigService.updateBranding(tenantId, branding);

        TenantBrandingUpdatedEvent event = applicationEvents
                .stream(TenantBrandingUpdatedEvent.class)
                .findFirst()
                .orElseThrow();

        assertThat(event.logoPublicUrl()).isNull();
    }
}
