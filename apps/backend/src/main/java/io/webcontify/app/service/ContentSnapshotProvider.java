package io.webcontify.app.service;

import io.webcontify.app.model.AppSnapshot;
import java.util.List;
import java.util.UUID;

/**
 * Provider interface for content definition snapshot data. Implemented by the content module to
 * avoid circular module dependencies (content depends on app, not vice versa).
 */
public interface ContentSnapshotProvider {

    List<AppSnapshot.AppSnapshotDefinition> getSnapshotDefinitions(UUID appId);
}
