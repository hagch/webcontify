package io.webcontify.identity.model;

import java.util.Map;
import java.util.UUID;

public record CreateRealmRequest(
        String tenantSlug,
        UUID tenantId,
        String displayName,
        SmtpConfig platformSmtpConfig,
        String loginTheme,
        String emailTheme,
        Map<String, String> initialAttributes
) {}
