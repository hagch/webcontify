package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.repositories.CollectionColumnRepository
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableRepository
import org.springframework.stereotype.Service

@Service
class CollectionService(
    val repository: CollectionRepository,
    val tableRepository: CollectionTableRepository,
    val columnRepository: CollectionColumnRepository
) {

  fun getAll(): Set<WebContifyCollectionDto> {
    return repository.getAll()
  }

  fun getById(id: Int): WebContifyCollectionDto {
    return repository.getById(id)
  }

  fun deleteById(id: Int) {
    val collection = repository.getById(id)
    return repository.deleteById(id).also { tableRepository.delete(collection.name) }
  }

  fun create(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    val createdCollection = repository.create(collection)
    val createdCollectionWithColumns =
        createdCollection.copy(
            columns =
                collection.columns?.map { column ->
                  columnRepository.create(column.copy(collectionId = createdCollection.id))
                })
    tableRepository.create(createdCollectionWithColumns)
    return createdCollectionWithColumns
  }

  fun update(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    val oldCollection = repository.getById(collection.id)
    return repository.update(collection).also {
      tableRepository.updateName(collection.name, oldCollection.name)
    }
  }
}
