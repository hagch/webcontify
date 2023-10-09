package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.repositories.CollectionItemRepository
import org.springframework.stereotype.Service

@Service
class CollectionItemService(
    val collectionService: CollectionService,
    val collectionItemRepository: CollectionItemRepository
) {

  fun getById(collectionId: Int, identifierMap: Map<String, String?>): Map<String, Any> {
    val collection = collectionService.getById(collectionId)
    return collectionItemRepository.getByIdFor(collection, identifierMap)
  }

  fun getById(collectionId: Int, itemId: String): Map<String, Any> {
    val collection = collectionService.getById(collectionId)
    val primaryKey = collection.columns?.first { it.isPrimaryKey } ?: throw RuntimeException()
    return collectionItemRepository.getByIdFor(
        collection, mapOf(Pair(primaryKey.name.lowercase(), itemId)))
  }

  fun create(collectionId: Int, item: Map<String, Any>): Map<String, Any> {
    val collection = collectionService.getById(collectionId)
    return collectionItemRepository.create(collection, item)
  }
}
