package io.webcontify.backend.collections

import io.webcontify.backend.collections.services.CollectionService
import io.webcontify.backend.models.WebContifyCollectionDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class CollectionController(val service: CollectionService) {

  @GetMapping("collections")
  fun get(): Set<String?> {
    return service.getAll().map { it.displayName }.toHashSet()
  }

  @GetMapping("collections/{id}")
  fun get(@PathVariable("id") id: Int): WebContifyCollectionDto {
    return service.getById(id)
  }
}
