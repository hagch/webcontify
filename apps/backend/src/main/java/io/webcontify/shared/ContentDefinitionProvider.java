package io.webcontify.shared;

import java.util.List;
import java.util.UUID;

/**
 * Provides content definition data to modules that must not depend on the content module directly.
 * Implemented by the content module; consumed by identity (RoleService) to avoid a cyclic dependency.
 */
public interface ContentDefinitionProvider {

    record DefinitionInfo(UUID id, String name) {}

    List<DefinitionInfo> findDefinitionsByAppId(UUID appId);
}
