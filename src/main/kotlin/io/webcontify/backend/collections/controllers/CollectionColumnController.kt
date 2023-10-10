package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.services.CollectionColumnService
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.springframework.web.bind.annotation.*

@RestController
class CollectionColumnController(val service: CollectionColumnService) {

  @DeleteMapping("$COLLECTIONS_PATH/{collectionId}/columns/{name}")
  fun delete(@PathVariable("collectionId") collectionId: Int, @PathVariable("name") name: String) {
    return service.deleteById(collectionId, name)
  }

  @GetMapping("$COLLECTIONS_PATH/{collectionId}/columns/{name}")
  fun getById(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("name") name: String
  ): WebContifyCollectionColumnDto {
    return service.getById(collectionId, name)
  }

  @GetMapping("$COLLECTIONS_PATH/{collectionId}/columns")
  fun getAllForCollection(
      @PathVariable("collectionId") collectionId: Int
  ): Set<WebContifyCollectionColumnDto> {
    return service.getAllForCollection(collectionId)
  }

  @PostMapping("$COLLECTIONS_PATH/{collectionId}/columns")
  fun create(@RequestBody column: WebContifyCollectionColumnDto): WebContifyCollectionColumnDto {
    return service.create(column)
  }

  @PutMapping("$COLLECTIONS_PATH/{collectionId}/columns/{name}")
  fun update(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("name") oldName: String,
      @RequestBody newColumn: WebContifyCollectionColumnDto
  ): WebContifyCollectionColumnDto {
    return service.update(oldName, newColumn)
  }
}
