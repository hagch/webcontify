package io.webcontify.tenant.event;

import java.util.UUID;

public record TenantDeletedEvent(UUID tenantId, String slug) {}
