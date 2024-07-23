package io.webcontify.backend.collections.mappers

import io.webcontify.backend.collections.utils.camelToSnakeCase
import io.webcontify.backend.collections.utils.snakeToCamelCase
import org.springframework.stereotype.Service

@Service
class ItemMapper {

  fun mapKeysToDataStore(item: Map<String, Any?>): Map<String, Any?> {
    return item.mapKeys { it.key.camelToSnakeCase() }
  }

  fun mapKeysToResponse(item: Map<String, Any?>): Map<String, Any?> {
    return item.mapKeys { it.key.snakeToCamelCase() }
  }
}
