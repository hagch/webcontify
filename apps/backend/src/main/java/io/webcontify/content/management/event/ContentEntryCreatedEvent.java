package io.webcontify.content.management.event;

import java.util.UUID;

public record ContentEntryCreatedEvent(UUID tenantId, UUID definitionId, UUID entryId) {}
