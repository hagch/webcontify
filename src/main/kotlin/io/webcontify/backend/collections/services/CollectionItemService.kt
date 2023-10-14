package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.repositories.CollectionItemRepository
import io.webcontify.backend.collections.utils.snakeToCamelCase
import org.springframework.stereotype.Service

@Service
class CollectionItemService(
    val collectionService: CollectionService,
    val collectionItemRepository: CollectionItemRepository
) {

  fun getById(collectionId: Int, identifierMap: Map<String, Any?>): Map<String, Any> {
    val collection = collectionService.getById(collectionId)
    return collectionItemRepository.getByIdFor(collection, identifierMap)
  }

  fun getById(collectionId: Int, itemId: Any): Map<String, Any> {
    val collection = collectionService.getById(collectionId)
    if (collection.columns == null) {
      throw UnprocessableContentException()
    }
    val primaryKey = collection.columns.first { it.isPrimaryKey }
    return collectionItemRepository.getByIdFor(
        collection, mapOf(Pair(primaryKey.name.lowercase(), itemId)))
  }

  fun deleteById(collectionId: Int, identifierMap: Map<String, Any?>) {
    val collection = collectionService.getById(collectionId)
    val primaryKeys = collection.columns?.filter { it.isPrimaryKey }
    if (primaryKeys?.size != identifierMap.size) {
      throw UnprocessableContentException()
    }
    primaryKeys.forEach {
      if (!identifierMap.containsKey(it.name.snakeToCamelCase())) {
        throw UnprocessableContentException()
      }
    }
    return collectionItemRepository.deleteById(collection, identifierMap)
  }

  fun deleteById(collectionId: Int, itemId: Any) {
    val collection = collectionService.getById(collectionId)
    if (collection.columns == null) {
      throw UnprocessableContentException()
    }
    val primaryKey = collection.columns.first { it.isPrimaryKey }
    return collectionItemRepository.deleteById(
        collection, mapOf(Pair(primaryKey.name.lowercase(), itemId)))
  }

  fun getAllFor(collectionId: Int): List<Map<String, Any>> {
    val collection = collectionService.getById(collectionId)
    return collectionItemRepository.getAllFor(collection)
  }

  fun create(collectionId: Int, item: Map<String, Any>): Map<String, Any> {
    val collection = collectionService.getById(collectionId)
    return collectionItemRepository.create(collection, item)
  }
}
