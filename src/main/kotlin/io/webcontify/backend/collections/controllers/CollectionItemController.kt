package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.services.CollectionItemService
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.springframework.web.bind.annotation.*

@RestController
class CollectionItemController(val collectionItemService: CollectionItemService) {

  @DeleteMapping("$COLLECTIONS_PATH/{collectionId}/items/{*slugOrId}")
  fun deleteById(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("slugOrId") slugOrId: String
  ) {
    slugOrId.substring(1).let {
      if (isSlug(it)) {
        return collectionItemService.deleteById(collectionId, mapSlugToMap(it))
      }
      return collectionItemService.deleteById(collectionId, it)
    }
  }

  @GetMapping("$COLLECTIONS_PATH/{collectionId}/items/{*slugOrId}")
  fun getById(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("slugOrId") slugOrId: String
  ): Map<String, Any> {
    slugOrId.substring(1).let {
      if (isSlug(it)) {
        return collectionItemService.getById(collectionId, mapSlugToMap(it))
      }
      return collectionItemService.getById(collectionId, it)
    }
  }

  @GetMapping("$COLLECTIONS_PATH/{collectionId}/items")
  fun getAllForCollection(@PathVariable("collectionId") collectionId: Int): List<Any> {
    return collectionItemService.getAllFor(collectionId)
  }

  @PostMapping("$COLLECTIONS_PATH/{collectionId}/items")
  fun create(
      @PathVariable("collectionId") collectionId: Int,
      @RequestBody item: Map<String, Any>
  ): Map<String, Any> { // TODO primary key generation and always insert primary keys
    return collectionItemService.create(collectionId, item)
  }

  @PutMapping("$COLLECTIONS_PATH/{collectionId}/items/{*identifierParameters}")
  fun updateById(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable identifierParameters: String? // TODO throw if empty and update functionality
  ): Map<String, Any> {
    return collectionItemService.getById(collectionId, mapSlugToMap(identifierParameters))
  }

  private fun mapSlugToMap(slug: String?): Map<String, String?> {
    return slug
        ?.split("/")
        .apply {
          if ((this?.size ?: 0) < 1) {
            throw RuntimeException() // TODO at least one primary key
          }
        }
        ?.chunked(2) {
          Pair(it.elementAtOrElse(0) { "" }.lowercase(), mapNullString(it.elementAtOrNull(1)))
        }
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
