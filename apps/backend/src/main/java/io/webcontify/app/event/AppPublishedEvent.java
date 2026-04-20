package io.webcontify.app.event;

import io.webcontify.shared.event.TenantScopedEvent;
import java.util.UUID;

public record AppPublishedEvent(UUID appId, UUID tenantId, String version) implements TenantScopedEvent {}
