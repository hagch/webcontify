package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiUpdateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.services.CollectionService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
class CollectionController(val service: CollectionService, val mapper: CollectionMapper) {

  @GetMapping("collections")
  fun get(): Set<WebContifyCollectionDto> {
    return service.getAll()
  }

  @PostMapping("collections")
  fun create(
      @RequestBody @Valid collection: WebContifyCollectionApiCreateRequest
  ): WebContifyCollectionDto {
    return service.create(mapper.mapApiToDto(collection))
  }

  @GetMapping("collections/{id}")
  fun getById(@PathVariable("id") id: Int): WebContifyCollectionDto {
    return service.getById(id)
  }

  @PutMapping("collections/{id}")
  fun update(
      @PathVariable("id") id: Int,
      @RequestBody @Valid collection: WebContifyCollectionApiUpdateRequest
  ): WebContifyCollectionDto {
    return service.update(mapper.mapApiToDto(collection, id))
  }

  @DeleteMapping("collections/{id}")
  fun delete(@PathVariable("id") id: Int) {
    return service.deleteById(id)
  }
}
