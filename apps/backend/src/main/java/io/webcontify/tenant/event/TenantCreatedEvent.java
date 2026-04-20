package io.webcontify.tenant.event;

import java.util.UUID;

public record TenantCreatedEvent(
    UUID tenantId,
    String slug,
    String adminEmail,
    String adminFirstName,
    String adminLastName
) {}
