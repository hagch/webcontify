package io.webcontify.tenant.api;

import io.webcontify.generated.api.TenantSelfServiceApi;
import io.webcontify.generated.model.TenantConfigResponse;
import io.webcontify.generated.model.UpdateBrandingConfigRequest;
import io.webcontify.shared.security.TenantAwareAuthentication;
import io.webcontify.tenant.mapper.TenantConfigMapper;
import io.webcontify.tenant.model.TenantConfig;
import io.webcontify.tenant.service.TenantConfigService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-Service Endpoints für Tenant-Admins zur Verwaltung der eigenen Konfiguration.
 * Die tenantId wird aus dem JWT-basierten SecurityContext gelesen — kein tenantId im URL-Pfad.
 * Platform Admins (ohne tenantId) erhalten 403 Forbidden.
 */
@RestController
public class TenantSelfServiceController implements TenantSelfServiceApi {

    private static final Logger log = LoggerFactory.getLogger(TenantSelfServiceController.class);

    private final TenantConfigService tenantConfigService;
    private final TenantConfigMapper tenantConfigMapper;

    public TenantSelfServiceController(
            TenantConfigService tenantConfigService,
            TenantConfigMapper tenantConfigMapper) {
        this.tenantConfigService = tenantConfigService;
        this.tenantConfigMapper = tenantConfigMapper;
    }

    @Override
    public ResponseEntity<TenantConfigResponse> getTenantSelfConfig() {
        UUID tenantId = resolveTenantId();
        if (tenantId == null) {
            log.warn("Platform admin attempted to call self-service endpoint GET /api/v1/tenant/config");
            return ResponseEntity.status(403).build();
        }
        TenantConfig config = tenantConfigService.getConfig(tenantId);
        return ResponseEntity.ok(tenantConfigMapper.toResponse(config));
    }

    @Override
    public ResponseEntity<TenantConfigResponse> updateTenantSelfBranding(
            UpdateBrandingConfigRequest updateBrandingConfigRequest) {
        UUID tenantId = resolveTenantId();
        if (tenantId == null) {
            log.warn("Platform admin attempted to call self-service endpoint PUT /api/v1/tenant/config/branding");
            return ResponseEntity.status(403).build();
        }
        TenantConfig config = tenantConfigService.updateBranding(
                tenantId, tenantConfigMapper.toDomain(updateBrandingConfigRequest));
        return ResponseEntity.ok(tenantConfigMapper.toResponse(config));
    }

    /**
     * Liest die tenantId aus dem SecurityContext.
     * Gibt null zurück wenn der authentifizierte User ein Platform Admin ist (kein Tenant).
     */
    private UUID resolveTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof TenantAwareAuthentication tenantAuth) {
            return tenantAuth.getTenantInformation().tenantId();
        }
        return null;
    }
}
