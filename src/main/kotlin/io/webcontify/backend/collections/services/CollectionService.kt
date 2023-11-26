package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.repositories.CollectionColumnRepository
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollectionService(
    val repository: CollectionRepository,
    val tableRepository: CollectionTableRepository,
    val columnRepository: CollectionColumnRepository,
    val collectionMapper: CollectionMapper
) {

  @Transactional(readOnly = true)
  fun getAll(): Set<WebContifyCollectionDto> {
    return repository.getAll()
  }

  @Transactional(readOnly = true)
  fun getById(id: Int): WebContifyCollectionDto {
    return repository.getById(id)
  }

  @Transactional
  fun deleteById(id: Int) {
    val collection = repository.getById(id)
    columnRepository.deleteAllForCollection(id)
    repository.deleteById(id)
    tableRepository.delete(collection.name)
  }

  @Transactional
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

  @Transactional
  fun update(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    val oldCollection = repository.getById(collection.id)
    return repository.update(collection).let {
      tableRepository.updateName(collection.name, oldCollection.name)
      collectionMapper.addColumnsToDto(it, columnRepository.getAllForCollection(it.id))
    }
  }
}
