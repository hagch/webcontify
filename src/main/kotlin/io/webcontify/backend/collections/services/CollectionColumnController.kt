package io.webcontify.backend.collections

import io.webcontify.backend.collections.services.CollectionColumnRepository
import io.webcontify.backend.models.WebContifyCollectionColumnDto
import org.springframework.web.bind.annotation.*

@RestController
class CollectionColumnController(val service: CollectionColumnRepository) {

  @GetMapping("collections/items")
  fun get(): Set<WebContifyCollectionColumnDto> {
    return service.getAll()
  }

  @PostMapping("collections/{collectionId}/items/{name}")
  fun create(@RequestBody column: WebContifyCollectionColumnDto): WebContifyCollectionColumnDto {
    return service.create(column)
  }

  @GetMapping("collections/{collectionId}/items/{name}")
  fun getById(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("name") name: String
  ): WebContifyCollectionColumnDto {
    return service.getById(collectionId, name)
  }

  @PutMapping("collections/{collectionId}/items/{name}")
  fun update(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("name") name: String,
      @RequestBody column: WebContifyCollectionColumnDto
  ): WebContifyCollectionColumnDto {
    return service.update(column)
  }

  @DeleteMapping("collections/{collectionId}/items/{name}")
  fun delete(@PathVariable("collectionId") collectionId: Int, @PathVariable("name") name: String) {
    return service.deleteById(collectionId, name)
  }

  @DeleteMapping("collections/{collectionId}/items")
  fun deleteAllForCollection(@PathVariable("collectionId") collectionId: Int) {
    return service.deleteAllForCollection(collectionId)
  }

  @GetMapping("collections/{collectionId}/items")
  fun getAllForCollection(
      @PathVariable("collectionId") collectionId: Int
  ): Set<WebContifyCollectionColumnDto> {
    return service.getAllForCollection(collectionId)
  }
}
