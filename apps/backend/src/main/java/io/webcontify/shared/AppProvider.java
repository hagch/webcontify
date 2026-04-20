package io.webcontify.shared;

import java.util.List;
import java.util.UUID;

/**
 * Provides app data to modules that must not depend on the app module directly.
 * Implemented by the app module; consumed by identity (RoleService) to avoid a cyclic dependency.
 */
public interface AppProvider {

    record AppInfo(UUID id, String name, String slug) {}

    List<AppInfo> findAllActiveApps();
}
