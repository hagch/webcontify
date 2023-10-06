package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.repositories.CollectionItemRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CollectionItemController(val collectionItemRepository: CollectionItemRepository) {

  @GetMapping("/collections/{id}/items")
  fun getById(
      @PathVariable("id") id: Int,
      @RequestBody primaryKeyValueMap: Map<String, Any>
  ): Map<String, Any> {
    return collectionItemRepository.getById(id, primaryKeyValueMap)
  }
}
