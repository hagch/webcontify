package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.repositories.CollectionItemRepository
import org.springframework.web.bind.annotation.*

@RestController
class CollectionItemController(val collectionItemRepository: CollectionItemRepository) {

  @GetMapping("/collections/{collectionId}/items/{*slugOrId}")
  fun getById(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("slugOrId") slugOrId: String
  ): Map<String, Any> {
    slugOrId.substring(1).let {
      if (isSlug(it)) {
        return collectionItemRepository.getById(collectionId, mapSlugToMap(it))
      }
      return collectionItemRepository.getById(collectionId, it)
    }
  }

  @PutMapping("/collections/{collectionId}/items/{*identifierParameters}")
  fun updateById(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable identifierParameters: String? // TODO throw if empty and update functionality
  ): Map<String, Any> {
    return collectionItemRepository.getById(collectionId, mapSlugToMap(identifierParameters))
  }

  @PostMapping("/collections/{collectionId}/items")
  fun create(
      @PathVariable("collectionId") collectionId: Int,
      @RequestBody item: Map<String, Any>
  ): Map<String, Any> { // TODO primary key generation and always insert primary keys
    return collectionItemRepository.create(collectionId, item)
  }

  @GetMapping("/collections/{collectionId}/items")
  fun getAllForCollection( // TODO throw if empty and get all functionality
  @PathVariable("collectionId") collectionId: Int): List<Any> {
    return listOf()
  }

  private fun mapSlugToMap(slug: String?): Map<String, String?> {
    return slug
        ?.split("/")
        .apply {
          if ((this?.size ?: 0) < 1) {
            throw RuntimeException() // TODO at least one primary key
          }
        }
        ?.chunked(2) { Pair(it[0], mapNullString(it.elementAtOrNull(1))) }
        ?.associateBy({ it.first }, { it.second })
        ?: throw RuntimeException()
  }

  private fun mapNullString(stringToCheck: String?): String? {
    return if (stringToCheck == "null") {
      null
    } else {
      stringToCheck
    }
  }

  private fun isSlug(slug: String): Boolean {
    return slug.contains("/")
  }
}
