package io.webcontify.app.event;

import java.util.UUID;

public record AppPublishedEvent(UUID appId, UUID tenantId, String version) {}
