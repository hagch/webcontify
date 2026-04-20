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

    default BrandingConfigResponse toBrandingResponse(BrandingConfig branding) {
        if (branding == null) {
            return null;
        }
        BrandingConfigResponse response = new BrandingConfigResponse();
        response.setLogoStorageKey(branding.logoStorageKey());
        response.setFaviconStorageKey(branding.faviconStorageKey());
        response.setPrimaryColor(branding.primaryColor());
        response.setSecondaryColor(branding.secondaryColor());
        response.setAccentColor(branding.accentColor());
        response.setDangerColor(branding.dangerColor());
        response.setSuccessColor(branding.successColor());
        response.setWarningColor(branding.warningColor());
        response.setFontFamily(branding.fontFamily() != null
                ? BrandingConfigResponse.FontFamilyEnum.fromValue(branding.fontFamily()) : null);
        response.setBorderRadius(branding.borderRadius() != null
                ? BrandingConfigResponse.BorderRadiusEnum.fromValue(branding.borderRadius()) : null);
        return response;
    }

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
        return toResponse(config, null, null);
    }

    default TenantConfigResponse toResponse(TenantConfig config, String name, String slug) {
        if (config == null) {
            TenantConfigResponse response = new TenantConfigResponse();
            response.setName(name);
            response.setSlug(slug);
            return response;
        }
        TenantConfigResponse response = new TenantConfigResponse();
        response.setName(name);
        response.setSlug(slug);
        response.setBranding(config.branding() != null ? toBrandingResponse(config.branding()) : null);
        response.setEmail(config.email() != null ? toEmailResponse(config.email()) : null);
        response.setDomain(config.domain() != null ? toDomainResponse(config.domain()) : null);
        return response;
    }

    default BrandingConfig toDomain(UpdateBrandingConfigRequest request) {
        return new BrandingConfig(
                request.getLogoStorageKey(),
                request.getFaviconStorageKey(),
                request.getPrimaryColor(),
                request.getSecondaryColor(),
                request.getAccentColor(),
                request.getDangerColor(),
                request.getSuccessColor(),
                request.getWarningColor(),
                request.getFontFamily() != null ? request.getFontFamily().getValue() : null,
                request.getBorderRadius() != null ? request.getBorderRadius().getValue() : null);
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
