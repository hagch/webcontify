package io.webcontify.shared;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves user UUIDs to human-readable display names.
 * Implemented by the identity module; consumed by content to enrich entry responses.
 */
public interface UserDisplayNameProvider {

    /**
     * Resolve a batch of user UUIDs to display names.
     * Missing or deleted users receive a fallback value (e.g. "Deleted User").
     * Null values in the input collection are silently ignored.
     */
    Map<UUID, String> resolveDisplayNames(Collection<UUID> userIds);
}
