package io.webcontify.backend.collections.services

import io.webcontify.backend.models.WebContifyCollectionDto
import org.springframework.stereotype.Component

@Component
class CollectionService(val repository: CollectionDao) {

  fun getAll(): Set<WebContifyCollectionDto> {
    return repository.getAll()
  }

  fun getById(id: Int): WebContifyCollectionDto {
    return repository.getById(id)
  }
}
