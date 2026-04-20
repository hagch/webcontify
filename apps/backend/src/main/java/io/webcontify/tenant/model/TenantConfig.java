package io.webcontify.tenant.model;

public record TenantConfig(
        BrandingConfig branding,
        EmailConfig email,
        DomainConfig domain) {

    public record BrandingConfig(
            String logoStorageKey,
            String faviconStorageKey,
            String primaryColor,
            String secondaryColor,
            String accentColor,
            String dangerColor,
            String successColor,
            String warningColor,
            String fontFamily,
            String borderRadius) {}

    public record EmailConfig(
            String smtpHost,
            Integer smtpPort,
            String smtpUser,
            String smtpPassword,
            String fromAddress,
            String fromName) {}

    public record DomainConfig(
            String customDomain,
            String sslStatus) {}
}
