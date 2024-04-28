package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.mappers.CollectionMapper
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiResponse
import io.webcontify.backend.collections.models.apis.WebContifyCollectionApiUpdateRequest
import io.webcontify.backend.collections.services.CollectionService
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
class CollectionController(val service: CollectionService, val mapper: CollectionMapper) {

  @DeleteMapping("$COLLECTIONS_PATH/{id}")
  fun delete(@PathVariable("id") id: Long): ResponseEntity<Void> {
    service.deleteById(id)
    return ResponseEntity.noContent().build()
  }

  @GetMapping(COLLECTIONS_PATH)
  fun get(): ResponseEntity<Set<WebContifyCollectionApiResponse>> {
    return ResponseEntity.ok(service.getAll().map { mapper.mapDtoToResponse(it) }.toSet())
  }

  @GetMapping("$COLLECTIONS_PATH/{id}")
  fun getById(@PathVariable("id") id: Long): ResponseEntity<WebContifyCollectionApiResponse> {
    return ResponseEntity.ok(mapper.mapDtoToResponse(service.getById(id)))
  }

  @PostMapping(COLLECTIONS_PATH)
  @Transactional
  fun create(
      @RequestBody @Valid collection: WebContifyCollectionApiCreateRequest
  ): ResponseEntity<WebContifyCollectionApiResponse> {
    val createdCollection = service.create(mapper.mapApiToDto(collection))
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.mapDtoToResponse(createdCollection))
  }

  @PutMapping("$COLLECTIONS_PATH/{id}")
  fun update(
      @PathVariable("id") id: Int,
      @RequestBody @Valid collection: WebContifyCollectionApiUpdateRequest
  ): ResponseEntity<WebContifyCollectionApiResponse> {
    return ResponseEntity.ok(
        mapper.mapDtoToResponse(service.update(mapper.mapApiToDto(collection, id))))
  }
}
