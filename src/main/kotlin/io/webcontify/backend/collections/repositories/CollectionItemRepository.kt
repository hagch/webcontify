package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.daos.CollectionItemDao
import org.springframework.stereotype.Component

@Component
class CollectionItemRepository(
    val collectionRepository: CollectionRepository,
    val collectionItemDao: CollectionItemDao
) {

  fun getById(collectionId: Int, identifierMap: Map<String, String?>): Map<String, Any> {
    val collection = collectionRepository.getById(collectionId)
    return collectionItemDao.getItemByIdFor(collection, identifierMap)
  }

  fun getById(collectionId: Int, itemId: String): Map<String, Any> {
    val collection = collectionRepository.getById(collectionId)
    val primaryKey = collection.columns?.first { it.isPrimaryKey } ?: throw RuntimeException()
    return collectionItemDao.getItemByIdFor(
        collection, mapOf(Pair(primaryKey.name.lowercase(), itemId)))
  }

  fun create(collectionId: Int, item: Map<String, Any>): Map<String, Any> {
    val collection = collectionRepository.getById(collectionId)
    return collectionItemDao.create(collection, item)
  }
}
