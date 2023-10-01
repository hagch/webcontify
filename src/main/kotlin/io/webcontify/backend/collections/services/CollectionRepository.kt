package io.webcontify.backend.collections.services

import io.webcontify.backend.models.WebContifyCollectionDto
import org.springframework.stereotype.Component

@Component
class CollectionRepository(val dao: CollectionDao) {

  fun getAll(): Set<WebContifyCollectionDto> {
    return dao.getAll()
  }

  fun getById(id: Int): WebContifyCollectionDto {
    return dao.getById(id)
  }

  fun deleteById(id: Int) {
    return dao.deleteById(id)
  }

  fun create(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    return dao.create(collection)
  }

  fun update(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    return dao.update(collection)
  }
}
