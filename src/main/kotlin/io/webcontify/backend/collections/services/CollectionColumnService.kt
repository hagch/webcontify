package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.repositories.CollectionColumnRepository
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableColumnRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollectionColumnService(
    val repository: CollectionColumnRepository,
    val collectionRepository: CollectionRepository,
    val tableColumnRepository: CollectionTableColumnRepository
) {

  @Transactional(readOnly = true)
  fun getAllForCollection(collectionId: Int): Set<WebContifyCollectionColumnDto> {
    return repository.getAllForCollection(collectionId)
  }

  @Transactional(readOnly = true)
  fun getById(collectionId: Int, name: String): WebContifyCollectionColumnDto {
    return repository.getById(collectionId, name)
  }

  @Transactional
  fun deleteById(collectionId: Int, name: String) {
    val collection = collectionRepository.getById(collectionId)
    val column =
        collection.getColumnWithName(name)
            ?: throw NotFoundException(ErrorCode.COLUMN_NOT_FOUND, collectionId.toString(), name)
    if (column.isPrimaryKey) {
      throw UnprocessableContentException(
          ErrorCode.COLUMN_IS_PRIMARY_COLUMN, name, collectionId.toString())
    }
    repository.deleteById(collectionId, name)
    tableColumnRepository.delete(collection, name)
  }

  @Transactional
  fun create(column: WebContifyCollectionColumnDto): WebContifyCollectionColumnDto {
    val collection = collectionRepository.getById(column.collectionId)
    tableColumnRepository.create(collection, column)
    return repository.create(column)
  }

  @Transactional
  fun createForCollection(
      collectionId: Int,
      columns: Collection<WebContifyCollectionColumnDto>?
  ): List<WebContifyCollectionColumnDto> {
    return columns?.map { create(it.copy(collectionId = collectionId)) } ?: listOf()
  }

  @Transactional
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
