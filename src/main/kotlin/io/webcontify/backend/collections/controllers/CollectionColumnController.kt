package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.services.CollectionColumnService
import org.springframework.web.bind.annotation.*

@RestController
class CollectionColumnController(val service: CollectionColumnService) {

  @GetMapping("collections/columns")
  fun get(): Set<WebContifyCollectionColumnDto> {
    return service.getAll()
  }

  @PostMapping("collections/{collectionId}/columns")
  fun create(@RequestBody column: WebContifyCollectionColumnDto): WebContifyCollectionColumnDto {
    return service.create(column)
  }

  @GetMapping("collections/{collectionId}/columns/{name}")
  fun getById(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("name") name: String
  ): WebContifyCollectionColumnDto {
    return service.getById(collectionId, name)
  }

  @PutMapping("collections/{collectionId}/columns/{name}")
  fun update(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("name") oldName: String,
      @RequestBody newColumn: WebContifyCollectionColumnDto
  ): WebContifyCollectionColumnDto {
    return service.update(oldName, newColumn)
  }

  @DeleteMapping("collections/{collectionId}/columns/{name}")
  fun delete(@PathVariable("collectionId") collectionId: Int, @PathVariable("name") name: String) {
    return service.deleteById(collectionId, name)
  }

  @GetMapping("collections/{collectionId}/columns")
  fun getAllForCollection(
      @PathVariable("collectionId") collectionId: Int
  ): Set<WebContifyCollectionColumnDto> {
    return service.getAllForCollection(collectionId)
  }
}
