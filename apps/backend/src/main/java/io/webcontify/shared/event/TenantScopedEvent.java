package io.webcontify.shared.event;

import java.util.UUID;

/**
 * Marker interface for events that carry a tenant context.
 * Used by {@link TenantAwareModuleListener} to automatically set the
 * SecurityContext before the transaction opens.
 */
public interface TenantScopedEvent {

    UUID tenantId();
}
