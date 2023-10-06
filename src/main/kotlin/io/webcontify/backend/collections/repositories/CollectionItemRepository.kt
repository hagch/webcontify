package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.daos.CollectionItemDao
import org.springframework.stereotype.Component

@Component
class CollectionItemRepository(
    val collectionRepository: CollectionRepository,
    val collectionItemDao: CollectionItemDao
) {

  fun getById(collectionId: Int, primaryKeysValueMap: Map<String, Any>): Map<String, Any> {
    return collectionItemDao.getItemByIdFor(
        collectionRepository.getById(collectionId).name, primaryKeysValueMap)
  }
}
