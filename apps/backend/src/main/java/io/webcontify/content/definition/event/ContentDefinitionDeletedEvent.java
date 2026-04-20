package io.webcontify.content.definition.event;

import java.util.UUID;

public record ContentDefinitionDeletedEvent(UUID appId, UUID tenantId, UUID definitionId) {}
