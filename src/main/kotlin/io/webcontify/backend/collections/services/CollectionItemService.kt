package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.IdentifierMap
import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.repositories.CollectionItemRepository
import io.webcontify.backend.collections.utils.snakeToCamelCase
import io.webcontify.backend.collections.utils.toKeyValueString
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollectionItemService(
    val collectionService: CollectionService,
    val collectionItemRepository: CollectionItemRepository
) {

  @Transactional(readOnly = true)
  fun getById(collectionId: Long, identifierMap: IdentifierMap): Item {
    val collection = collectionService.getById(collectionId)
    return collectionItemRepository.getByIdFor(collection, identifierMap)
  }

  @Transactional(readOnly = true)
  fun getById(collectionId: Long, itemId: Any): Item {
    val collection = collectionService.getById(collectionId)
    if (collection.fields == null) {
      throw NotFoundException(ErrorCode.GET_ITEM_COLLECTION_WITHOUT_FIELDS)
    }
    val primaryKey = collection.fields.first { it.isPrimaryKey }
    return collectionItemRepository.getByIdFor(
        collection, mapOf(Pair(primaryKey.name.lowercase(), itemId)))
  }

  @Transactional
  fun deleteById(collectionId: Long, identifierMap: IdentifierMap) {
    val collection = collectionService.getById(collectionId)
    val primaryKeys = collection.fields?.filter { it.isPrimaryKey }
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

  @Transactional
  fun deleteById(collectionId: Long, itemId: Any) {
    val collection = collectionService.getById(collectionId)
    if (collection.fields == null) {
      throw UnprocessableContentException(ErrorCode.DELETE_ITEM_FROM_COLLECTION_WITHOUT_FIELDS)
    }
    val primaryKey = collection.fields.first { it.isPrimaryKey }
    return collectionItemRepository.deleteById(
        collection, mapOf(Pair(primaryKey.name.lowercase(), itemId)))
  }

  @Transactional(readOnly = true)
  fun getAllFor(collectionId: Long): List<Item> {
    val collection = collectionService.getById(collectionId)
    return collectionItemRepository.getAllFor(collection)
  }

  @Transactional
  fun create(collectionId: Long, item: Item): Item {
    val collection = collectionService.getById(collectionId)
    val mirrorFields =
        collection.fields
            ?.filter { it.type == WebcontifyCollectionFieldType.RELATION_MIRROR }
            ?.mapNotNull { item[it.name] }
    if (mirrorFields?.isNotEmpty() == true) {
      throw UnprocessableContentException(ErrorCode.MIRROR_FIELD_INCLUDED)
    }
    return collectionItemRepository.create(collection, item)
  }

  @Transactional
  fun updateById(collectionId: Long, identifierMap: IdentifierMap, item: Item): Item {
    val collection = collectionService.getById(collectionId)
    if (collection.fields == null) {
      throw UnprocessableContentException(ErrorCode.UPDATE_ITEM_FROM_COLLECTION_WITHOUT_FIELDS)
    }
    val primaryKeys = collection.fields.filter { it.isPrimaryKey }
    val updateAbleItem = item.toMutableMap()
    validatePrimaryKeys(primaryKeys, identifierMap, updateAbleItem)
    for (primaryKey in primaryKeys) {
      updateAbleItem.remove(primaryKey.name)
    }
    if (updateAbleItem.isEmpty()) {
      throw UnprocessableContentException(
          ErrorCode.NO_FIELDS_TO_UPDATE, identifierMap.toKeyValueString(), collectionId.toString())
    }
    val mirrorFields =
        collection.fields
            .filter { it.type == WebcontifyCollectionFieldType.RELATION_MIRROR }
            .mapNotNull { updateAbleItem[it.name] }
    if (mirrorFields.isNotEmpty()) {
      throw UnprocessableContentException(ErrorCode.MIRROR_FIELD_INCLUDED)
    }
    return collectionItemRepository.update(collection, identifierMap, updateAbleItem)
  }

  @Transactional
  fun updateById(collectionId: Long, itemId: Any, item: Item): Item {
    val collection = collectionService.getById(collectionId)
    if (collection.fields == null) {
      throw UnprocessableContentException(ErrorCode.UPDATE_ITEM_FROM_COLLECTION_WITHOUT_FIELDS)
    }
    val primaryKey = collection.fields.first { it.isPrimaryKey }
    val updateAbleItem = item.toMutableMap()
    updateAbleItem.remove(primaryKey.name)
    if (updateAbleItem.isEmpty()) {
      throw UnprocessableContentException(
          ErrorCode.NO_FIELDS_TO_UPDATE, "${primaryKey.name}= $itemId", collectionId.toString())
    }
    return collectionItemRepository.update(
        collection, mapOf(Pair(primaryKey.name.lowercase(), itemId)), updateAbleItem)
  }

  private fun validatePrimaryKeys(
      primaryKeys: List<WebContifyCollectionFieldDto>,
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
