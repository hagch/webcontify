package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.IdentifierMap
import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.models.apis.ErrorCode
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.repositories.CollectionItemRepository
import io.webcontify.backend.collections.utils.snakeToCamelCase
import org.springframework.stereotype.Service

@Service
class CollectionItemService(
    val collectionService: CollectionService,
    val collectionItemRepository: CollectionItemRepository
) {

  fun getById(collectionId: Int, identifierMap: IdentifierMap): Item {
    val collection = collectionService.getById(collectionId)
    return collectionItemRepository.getByIdFor(collection, identifierMap)
  }

  fun getById(collectionId: Int, itemId: Any): Item {
    val collection = collectionService.getById(collectionId)
    if (collection.columns == null) {
      throw NotFoundException(ErrorCode.GET_ITEM_COLLECTION_WITHOUT_COLUMNS)
    }
    val primaryKey = collection.columns.first { it.isPrimaryKey }
    return collectionItemRepository.getByIdFor(
        collection, mapOf(Pair(primaryKey.name.lowercase(), itemId)))
  }

  fun deleteById(collectionId: Int, identifierMap: IdentifierMap) {
    val collection = collectionService.getById(collectionId)
    val primaryKeys = collection.columns?.filter { it.isPrimaryKey }
    if (primaryKeys?.size != identifierMap.size) {
      throw UnprocessableContentException(ErrorCode.PRIMARY_KEYS_UNEQUAL)
    }
    primaryKeys.forEach {
      if (!identifierMap.containsKey(it.name.snakeToCamelCase().lowercase())) {
        throw UnprocessableContentException(
            ErrorCode.PRIMARY_KEY_NOT_INCLUDED, it.name.snakeToCamelCase())
      }
    }
    return collectionItemRepository.deleteById(collection, identifierMap)
  }

  fun deleteById(collectionId: Int, itemId: Any) {
    val collection = collectionService.getById(collectionId)
    if (collection.columns == null) {
      throw UnprocessableContentException(ErrorCode.DELETE_ITEM_FROM_COLLECTION_WITHOUT_COLUMNS)
    }
    val primaryKey = collection.columns.first { it.isPrimaryKey }
    return collectionItemRepository.deleteById(
        collection, mapOf(Pair(primaryKey.name.lowercase(), itemId)))
  }

  fun getAllFor(collectionId: Int): List<Item> {
    val collection = collectionService.getById(collectionId)
    return collectionItemRepository.getAllFor(collection)
  }

  fun create(collectionId: Int, item: Item): Item {
    val collection = collectionService.getById(collectionId)
    return collectionItemRepository.create(collection, item)
  }

  fun updateById(collectionId: Int, identifierMap: IdentifierMap, item: Item): Item {
    val collection = collectionService.getById(collectionId)
    if (collection.columns == null) {
      throw UnprocessableContentException(ErrorCode.UPDATE_ITEM_FROM_COLLECTION_WITHOUT_COLUMNS)
    }
    val primaryKeys = collection.columns.filter { it.isPrimaryKey }
    val updateAbleItem = item.toMutableMap()
    validatePrimaryKeys(primaryKeys, identifierMap, updateAbleItem)
    return collectionItemRepository.update(collection, identifierMap, updateAbleItem)
  }

  fun updateById(collectionId: Int, itemId: Any, item: Item): Item {
    val collection = collectionService.getById(collectionId)
    if (collection.columns == null) {
      throw UnprocessableContentException(ErrorCode.UPDATE_ITEM_FROM_COLLECTION_WITHOUT_COLUMNS)
    }
    val primaryKey = collection.columns.first { it.isPrimaryKey }
    return collectionItemRepository.update(
        collection, mapOf(Pair(primaryKey.name.lowercase(), itemId)), item)
  }

  private fun validatePrimaryKeys(
      primaryKeys: List<WebContifyCollectionColumnDto>,
      identifierMap: IdentifierMap,
      updateAbleItem: MutableMap<String, Any?>
  ) {
    if (primaryKeys.size != identifierMap.size) {
      throw UnprocessableContentException(ErrorCode.PRIMARY_KEYS_UNEQUAL)
    }

    primaryKeys.map {
      val camelCaseName = it.name.snakeToCamelCase()
      updateAbleItem -= camelCaseName
      if (!identifierMap.containsKey(camelCaseName.lowercase())) {
        throw UnprocessableContentException(
            ErrorCode.PRIMARY_KEY_NOT_INCLUDED, it.name.snakeToCamelCase())
      }
    }
  }
}
