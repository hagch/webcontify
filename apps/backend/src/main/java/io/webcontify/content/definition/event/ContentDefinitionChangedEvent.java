package io.webcontify.content.definition.event;

import java.util.UUID;

public record ContentDefinitionChangedEvent(UUID appId, UUID tenantId) {}
