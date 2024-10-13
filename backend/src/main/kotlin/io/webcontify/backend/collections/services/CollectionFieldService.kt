package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.repositories.CollectionFieldRepository
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableFieldRepository
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollectionFieldService(
    val repository: CollectionFieldRepository,
    val collectionRepository: CollectionRepository,
    val tableFieldRepository: CollectionTableFieldRepository
) {

  @Transactional(readOnly = true)
  fun getAllForCollection(collectionId: Long): Set<WebContifyCollectionFieldDto> {
    return repository.getAllForCollection(collectionId)
  }

  @Transactional(readOnly = true)
  fun getById(collectionId: Long, id: Long): WebContifyCollectionFieldDto {
    return repository.getById(collectionId, id)
  }

  @Transactional
  fun deleteById(collectionId: Long, id: Long) {
    val collection = collectionRepository.getById(collectionId)
    val field =
        collection.getFieldWithId(id)
            ?: throw NotFoundException(
                ErrorCode.FIELD_NOT_FOUND, collectionId.toString(), id.toString())
    if (field.isPrimaryKey) {
      throw UnprocessableContentException(
          ErrorCode.FIELD_IS_PRIMARY_FIELD, id.toString(), collectionId.toString())
    }
    repository.deleteById(collectionId, id)
    tableFieldRepository.delete(collection, field.name)
  }

  @Transactional
  fun create(field: WebContifyCollectionFieldDto): WebContifyCollectionFieldDto {
    if (field.type == WebcontifyCollectionFieldType.RELATION_MIRROR) {
      throw UnprocessableContentException(ErrorCode.MIRROR_FIELD_INCLUDED)
    }
    val collection = collectionRepository.getById(field.collectionId)
    tableFieldRepository.create(collection, field)
    return repository.create(field)
  }

  @Transactional
  fun createForCollection(
      collectionId: Long,
      fields: Collection<WebContifyCollectionFieldDto>?
  ): List<WebContifyCollectionFieldDto> {
    return fields?.map { create(it.copy(collectionId = collectionId)) } ?: listOf()
  }

  @Transactional
  fun update(id: Long, newField: WebContifyCollectionFieldDto): WebContifyCollectionFieldDto {
    val collection = collectionRepository.getById(newField.collectionId)
    val field = repository.update(newField, id)
    if (collection.getFieldWithId(id)?.type != WebcontifyCollectionFieldType.RELATION_MIRROR) {
      tableFieldRepository.update(collection, newField, id)
    }
    return field
  }
}
