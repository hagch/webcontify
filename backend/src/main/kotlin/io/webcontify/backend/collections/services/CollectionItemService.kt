package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.IdentifierMap
import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.repositories.CollectionItemRepository
import io.webcontify.backend.collections.utils.toKeyValueString
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import io.webcontify.backend.relations.RelationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CollectionItemService(
    val collectionService: CollectionService,
    val collectionItemRepository: CollectionItemRepository,
    val relationService: RelationService
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
    return collectionItemRepository.getByIdFor(collection, mapOf(Pair(primaryKey.name, itemId)))
  }

  @Transactional
  fun deleteById(collectionId: Long, identifierMap: IdentifierMap) {
    val collection = collectionService.getById(collectionId)
    val primaryKeys = collection.fields?.filter { it.isPrimaryKey }
    if (primaryKeys?.size != identifierMap.size) {
      throw UnprocessableContentException(ErrorCode.PRIMARY_KEYS_UNEQUAL)
    }
    primaryKeys.forEach {
      if (!identifierMap.containsKey(it.name)) {
        throw UnprocessableContentException(ErrorCode.PRIMARY_KEY_NOT_INCLUDED, it.name)
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
    return collectionItemRepository.deleteById(collection, mapOf(Pair(primaryKey.name, itemId)))
  }

  @Transactional(readOnly = true)
  fun getAllFor(collectionId: Long): List<Item> {
    val collection = collectionService.getById(collectionId)
    return collectionItemRepository.getAllFor(collection)
  }

  @Transactional
  fun create(collectionId: Long, item: Item): Item {
    val collection = collectionService.getById(collectionId)
    val shouldFilterOutPrimaryKeys = !relationService.isUsedAsMappingTable(collectionId)
    var createAbleItem = item
    if (shouldFilterOutPrimaryKeys) {
      createAbleItem = filterOutNotCreatablePrimaryKeys(collection, item)
    }
    return collectionItemRepository.create(collection, createAbleItem)
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
        collection, mapOf(Pair(primaryKey.name, itemId)), updateAbleItem)
  }

  private fun filterOutNotCreatablePrimaryKeys(
      collection: WebContifyCollectionDto,
      item: Item
  ): Item {
    val removeAblePrimaryKeys =
        collection.fields
            ?.filter { it.isPrimaryKey && it.type == WebcontifyCollectionFieldType.NUMBER }
            ?.associate { it.name to true } ?: emptyMap()
    val itemWithoutRemovablePrimaryKeys =
        item.filter { !(removeAblePrimaryKeys.containsKey(it.key)) }
    return itemWithoutRemovablePrimaryKeys
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
      val camelCaseName = it.name
      updateAbleItem -= camelCaseName
      if (!identifierMap.containsKey(camelCaseName)) {
        throw UnprocessableContentException(ErrorCode.PRIMARY_KEY_NOT_INCLUDED, camelCaseName)
      }
    }
  }
}
