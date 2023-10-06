package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.models.WebContifyCollectionDto
import io.webcontify.backend.collections.repositories.CollectionRepository
import org.springframework.web.bind.annotation.*

@RestController
class CollectionController(val service: CollectionRepository) {

  @GetMapping("collections")
  fun get(): Set<WebContifyCollectionDto> {
    return service.getAll()
  }

  @PostMapping("collections")
  fun create(@RequestBody collection: WebContifyCollectionDto): WebContifyCollectionDto {
    return service.create(collection)
  }

  @GetMapping("collections/{id}")
  fun getById(@PathVariable("id") id: Int): WebContifyCollectionDto {
    return service.getById(id)
  }

  @PutMapping("collections/{id}")
  fun update(
      @PathVariable("id") id: Int,
      @RequestBody collection: WebContifyCollectionDto
  ): WebContifyCollectionDto {
    return service.update(collection)
  }

  @DeleteMapping("collections/{id}")
  fun delete(@PathVariable("id") id: Int): Unit {
    return service.deleteById(id)
  }
}
