package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableRepository
import org.springframework.stereotype.Service

@Service
class CollectionService(
    val repository: CollectionRepository,
    val tableRepository: CollectionTableRepository,
    val columnService: CollectionColumnService
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
    return repository.create(collection).also {
      if (it.id != null) {
        val columns = columnService.createForCollection(it.id, collection.columns)
        tableRepository.create(it.copy(columns = columns))
      }
    }
  }

  fun update(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    val oldCollection = repository.getById(collection.id)
    return repository.update(collection).also {
      tableRepository.updateName(it.name, oldCollection.name)
    }
  }
}
