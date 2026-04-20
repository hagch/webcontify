package io.webcontify.shared.event;

import java.util.UUID;

public record ContentDefinitionChangedEvent(UUID appId, UUID tenantId) {}
