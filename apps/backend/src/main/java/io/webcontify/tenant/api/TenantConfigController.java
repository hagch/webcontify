package io.webcontify.tenant.api;

import io.webcontify.generated.api.TenantConfigApi;
import io.webcontify.generated.model.TenantConfigResponse;
import io.webcontify.generated.model.TestEmailRequest;
import io.webcontify.generated.model.UpdateBrandingConfigRequest;
import io.webcontify.generated.model.UpdateDomainConfigRequest;
import io.webcontify.generated.model.UpdateEmailConfigRequest;
import io.webcontify.shared.email.EmailMessage;
import io.webcontify.shared.email.EmailService;
import io.webcontify.tenant.mapper.TenantConfigMapper;
import io.webcontify.tenant.model.TenantConfig;
import io.webcontify.tenant.service.TenantConfigService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TenantConfigController implements TenantConfigApi {

    private final TenantConfigService tenantConfigService;
    private final TenantConfigMapper tenantConfigMapper;
    private final EmailService emailService;

    public TenantConfigController(
            TenantConfigService tenantConfigService,
            TenantConfigMapper tenantConfigMapper,
            EmailService emailService) {
        this.tenantConfigService = tenantConfigService;
        this.tenantConfigMapper = tenantConfigMapper;
        this.emailService = emailService;
    }

    @Override
    public ResponseEntity<TenantConfigResponse> getAdminTenantConfig(UUID tenantId) {
        TenantConfig config = tenantConfigService.getConfig(tenantId);
        return ResponseEntity.ok(tenantConfigMapper.toResponse(config));
    }

    @Override
    public ResponseEntity<TenantConfigResponse> updateAdminBrandingConfig(
            UUID tenantId, UpdateBrandingConfigRequest updateBrandingConfigRequest) {
        TenantConfig config = tenantConfigService.updateBranding(
                tenantId, tenantConfigMapper.toDomain(updateBrandingConfigRequest));
        return ResponseEntity.ok(tenantConfigMapper.toResponse(config));
    }

    @Override
    public ResponseEntity<TenantConfigResponse> updateAdminEmailConfig(
            UUID tenantId, UpdateEmailConfigRequest updateEmailConfigRequest) {
        TenantConfig config = tenantConfigService.updateEmail(
                tenantId, tenantConfigMapper.toDomain(updateEmailConfigRequest));
        return ResponseEntity.ok(tenantConfigMapper.toResponse(config));
    }

    @Override
    public ResponseEntity<TenantConfigResponse> updateAdminDomainConfig(
            UUID tenantId, UpdateDomainConfigRequest updateDomainConfigRequest) {
        TenantConfig config = tenantConfigService.updateDomain(
                tenantId, tenantConfigMapper.toDomain(updateDomainConfigRequest));
        return ResponseEntity.ok(tenantConfigMapper.toResponse(config));
    }

    @Override
    public ResponseEntity<Void> testAdminEmailConfig(UUID tenantId, TestEmailRequest testEmailRequest) {
        TenantConfig config = tenantConfigService.getConfig(tenantId);
        TenantConfig.EmailConfig emailConfig = config.email();

        String fromAddress = emailConfig != null && emailConfig.fromAddress() != null
                ? emailConfig.fromAddress()
                : "noreply@webcontify.io";
        String fromName = emailConfig != null ? emailConfig.fromName() : null;

        EmailMessage message = new EmailMessage(
                testEmailRequest.getRecipientEmail(),
                "Webcontify — Test Email",
                "<p>Dies ist eine Test-E-Mail von Webcontify. Ihre E-Mail-Konfiguration funktioniert korrekt.</p>",
                fromAddress,
                fromName);

        emailService.send(message);
        return ResponseEntity.noContent().build();
    }
}
