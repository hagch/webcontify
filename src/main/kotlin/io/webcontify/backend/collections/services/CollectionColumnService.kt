package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.repositories.CollectionColumnRepository
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableColumnRepository
import org.springframework.stereotype.Service

@Service
class CollectionColumnService(
    val repository: CollectionColumnRepository,
    val collectionRepository: CollectionRepository,
    val tableColumnRepository: CollectionTableColumnRepository
) {

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
    return repository.deleteById(collectionId, name).also {
      val collection = collectionRepository.getById(collectionId)
      tableColumnRepository.delete(collection, name)
    }
  }

  fun create(column: WebContifyCollectionColumnDto): WebContifyCollectionColumnDto {
    return repository.create(column).also {
      val collection = collectionRepository.getById(it.collectionId)
      tableColumnRepository.create(collection, column)
    }
  }

  fun update(
      oldName: String,
      newColumn: WebContifyCollectionColumnDto
  ): WebContifyCollectionColumnDto {
    val collection = collectionRepository.getById(newColumn.collectionId)
    return repository.update(newColumn, oldName).also {
      tableColumnRepository.update(collection, newColumn, oldName)
    }
  }
}
