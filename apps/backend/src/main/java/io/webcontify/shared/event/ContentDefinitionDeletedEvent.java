package io.webcontify.shared.event;

import java.util.UUID;

public record ContentDefinitionDeletedEvent(UUID appId, UUID tenantId, UUID definitionId) {}
