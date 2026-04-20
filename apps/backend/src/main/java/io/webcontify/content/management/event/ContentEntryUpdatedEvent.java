package io.webcontify.content.management.event;

import java.util.Set;
import java.util.UUID;

public record ContentEntryUpdatedEvent(UUID tenantId, UUID definitionId, UUID entryId, Set<String> changedFieldKeys) {}
