package io.webcontify.tenant.mapper;

import io.webcontify.generated.model.*;
import io.webcontify.generated.model.DomainConfigResponse.SslStatusEnum;
import io.webcontify.tenant.model.TenantConfig;
import io.webcontify.tenant.model.TenantConfig.BrandingConfig;
import io.webcontify.tenant.model.TenantConfig.DomainConfig;
import io.webcontify.tenant.model.TenantConfig.EmailConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface TenantConfigMapper {

    BrandingConfigResponse toBrandingResponse(BrandingConfig branding);

    EmailConfigResponse toEmailResponse(EmailConfig email);

    default DomainConfigResponse toDomainResponse(DomainConfig domain) {
        if (domain == null) {
            return null;
        }
        DomainConfigResponse response = new DomainConfigResponse();
        response.setCustomDomain(domain.customDomain());
        response.setSslStatus(stringToSslStatus(domain.sslStatus()));
        return response;
    }

    @Named("stringToSslStatus")
    default SslStatusEnum stringToSslStatus(String sslStatus) {
        return sslStatus != null ? SslStatusEnum.fromValue(sslStatus) : null;
    }

    default TenantConfigResponse toResponse(TenantConfig config) {
        if (config == null) {
            return new TenantConfigResponse();
        }
        TenantConfigResponse response = new TenantConfigResponse();
        response.setBranding(config.branding() != null ? toBrandingResponse(config.branding()) : null);
        response.setEmail(config.email() != null ? toEmailResponse(config.email()) : null);
        response.setDomain(config.domain() != null ? toDomainResponse(config.domain()) : null);
        return response;
    }

    default BrandingConfig toDomain(UpdateBrandingConfigRequest request) {
        return new BrandingConfig(
                null,
                null,
                request.getPrimaryColor(),
                request.getSecondaryColor());
    }

    default EmailConfig toDomain(UpdateEmailConfigRequest request) {
        return new EmailConfig(
                request.getSmtpHost(),
                request.getSmtpPort(),
                request.getSmtpUser(),
                request.getSmtpPassword(),
                request.getFromAddress(),
                request.getFromName());
    }

    default DomainConfig toDomain(UpdateDomainConfigRequest request) {
        return new DomainConfig(
                request.getCustomDomain(),
                null);
    }
}
