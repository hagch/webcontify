package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.models.apis.WebContifyCollectionColumnApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionColumnApiUpdateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.services.CollectionColumnService
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
class CollectionColumnController(
    val service: CollectionColumnService,
    val mapper: CollectionMapper
) {

  @DeleteMapping("$COLLECTIONS_PATH/{collectionId}/columns/{name}")
  fun delete(@PathVariable("collectionId") collectionId: Int, @PathVariable("name") name: String) {
    return service.deleteById(collectionId, name.lowercase())
  }

  @GetMapping("$COLLECTIONS_PATH/{collectionId}/columns/{name}")
  fun getById(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("name") name: String
  ): WebContifyCollectionColumnDto {
    return service.getById(collectionId, name.lowercase())
  }

  @GetMapping("$COLLECTIONS_PATH/{collectionId}/columns")
  fun getAllForCollection(
      @PathVariable("collectionId") collectionId: Int
  ): Set<WebContifyCollectionColumnDto> {
    return service.getAllForCollection(collectionId)
  }

  @PostMapping("$COLLECTIONS_PATH/{collectionId}/columns")
  fun create(
      @PathVariable collectionId: Int,
      @RequestBody @Valid column: WebContifyCollectionColumnApiCreateRequest
  ): WebContifyCollectionColumnDto {
    return service.create(mapper.mapApiToDto(column, collectionId))
  }

  @PutMapping("$COLLECTIONS_PATH/{collectionId}/columns/{name}")
  fun update(
      @PathVariable("collectionId") collectionId: Int,
      @PathVariable("name") oldName: String,
      @RequestBody @Valid newColumn: WebContifyCollectionColumnApiUpdateRequest
  ): WebContifyCollectionColumnDto {
    return service.update(oldName.lowercase(), mapper.mapApiToDto(newColumn, collectionId))
  }
}
