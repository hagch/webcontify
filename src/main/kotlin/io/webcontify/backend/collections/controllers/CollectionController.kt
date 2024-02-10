package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiUpdateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.services.CollectionService
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import jakarta.validation.Valid
import org.springframework.util.CollectionUtils
import org.springframework.web.bind.annotation.*

@RestController
class CollectionController(
    val service: CollectionService,
    val mapper: CollectionMapper,
    val relationController: CollectionRelationController
) {

  @DeleteMapping("$COLLECTIONS_PATH/{id}")
  fun delete(@PathVariable("id") id: Int) {
    return service.deleteById(id)
  }

  @GetMapping(COLLECTIONS_PATH)
  fun get(): Set<WebContifyCollectionDto> {
    return service.getAll()
  }

  @GetMapping("$COLLECTIONS_PATH/{id}")
  fun getById(@PathVariable("id") id: Int): WebContifyCollectionDto {
    return service.getById(id)
  }

  @PostMapping(COLLECTIONS_PATH)
  fun create(
      @RequestBody @Valid collection: WebContifyCollectionApiCreateRequest
  ): WebContifyCollectionDto {
    var createdCollection = service.create(mapper.mapApiToDto(collection))
    if (!CollectionUtils.isEmpty(collection.relations)) {
      val relations =
          collection.relations?.map { relationController.create(createdCollection.id!!, it) }
      createdCollection = createdCollection.copy(relations = relations)
    }
    return createdCollection
  }

  @PutMapping("$COLLECTIONS_PATH/{id}")
  fun update(
      @PathVariable("id") id: Int,
      @RequestBody @Valid collection: WebContifyCollectionApiUpdateRequest
  ): WebContifyCollectionDto {
    return service.update(mapper.mapApiToDto(collection, id))
  }
}
