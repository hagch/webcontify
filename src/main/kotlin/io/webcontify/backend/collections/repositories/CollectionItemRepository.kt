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

  fun create(collectionId: Int, item: Map<String,Any>): Map<String, Any> {
    val collection = collectionRepository.getById(collectionId)
    return collectionItemDao.create(collection, item)
  }
}
