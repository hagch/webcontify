package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.repositories.CollectionColumnRepository
import org.springframework.stereotype.Service

@Service
class CollectionColumnService(val repository: CollectionColumnRepository) {

  fun getAll(): Set<WebContifyCollectionColumnDto> {
    return repository.getAll()
  }

  fun getAllForCollection(collectionId: Int): Set<WebContifyCollectionColumnDto> {
    return repository.getAllForCollection(collectionId)
  }

  fun getById(collectionId: Int, name: String): WebContifyCollectionColumnDto {
    return repository.getById(collectionId, name)
  }

  fun deleteById(collectionId: Int, name: String) {
    return repository.deleteById(collectionId, name)
  }

  fun create(column: WebContifyCollectionColumnDto): WebContifyCollectionColumnDto {
    return repository.create(column)
  }

  fun update(column: WebContifyCollectionColumnDto): WebContifyCollectionColumnDto {
    return repository.update(column)
  }

  fun deleteAllForCollection(columnId: Int) {
    return repository.deleteAllForCollection(columnId)
  }
}
