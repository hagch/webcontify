package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.repositories.CollectionFieldRepository
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollectionService(
    val repository: CollectionRepository,
    val tableRepository: CollectionTableRepository,
    val fieldRepository: CollectionFieldRepository,
    val collectionMapper: CollectionMapper
) {

  @Transactional(readOnly = true)
  fun getAll(): Set<WebContifyCollectionDto> {
    return repository.getAll()
  }

  @Transactional(readOnly = true)
  fun getById(id: Long): WebContifyCollectionDto {
    return repository.getById(id)
  }

  @Transactional
  fun deleteById(id: Long) {
    val collection = repository.getById(id)
    repository.deleteById(id)
    tableRepository.delete(collection.name)
  }

  @Transactional
  fun create(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    val createdCollection = repository.create(collection)
    val createdCollectionWithFields =
        createdCollection.copy(
            fields =
                collection.fields?.map { field ->
                  fieldRepository.create(field.copy(collectionId = createdCollection.id))
                })
    tableRepository.create(createdCollectionWithFields)
    return createdCollectionWithFields
  }

  @Transactional
  fun update(collection: WebContifyCollectionDto): WebContifyCollectionDto {
    val oldCollection = repository.getById(collection.id)
    return repository.update(collection).let {
      tableRepository.updateName(collection.name, oldCollection.name)
      collectionMapper.addFieldsToDto(it, oldCollection.fields!!.toSet())
    }
  }
}
