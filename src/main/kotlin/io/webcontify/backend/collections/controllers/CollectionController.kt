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
import org.springframework.util.CollectionUtils
import org.springframework.web.bind.annotation.*

@RestController
class CollectionController(
    val service: CollectionService,
    val mapper: CollectionMapper,
    val relationController: CollectionRelationController
) {

  @DeleteMapping("$COLLECTIONS_PATH/{id}")
  fun delete(@PathVariable("id") id: Int): ResponseEntity<Void> {
    service.deleteById(id)
    return ResponseEntity.noContent().build()
  }

  @GetMapping(COLLECTIONS_PATH)
  fun get(): ResponseEntity<Set<WebContifyCollectionApiResponse>> {
    return ResponseEntity.ok(service.getAll().map { mapper.mapDtoToResponse(it) }.toSet())
  }

  @GetMapping("$COLLECTIONS_PATH/{id}")
  fun getById(@PathVariable("id") id: Int): ResponseEntity<WebContifyCollectionApiResponse> {
    return ResponseEntity.ok(mapper.mapDtoToResponse(service.getById(id)))
  }

  @PostMapping(COLLECTIONS_PATH)
  @Transactional
  fun create(
      @RequestBody @Valid collection: WebContifyCollectionApiCreateRequest
  ): ResponseEntity<WebContifyCollectionApiResponse> {
    var createdCollection = service.create(mapper.mapApiToDto(collection))
    if (!CollectionUtils.isEmpty(collection.relations)) {
      val relations =
          collection.relations!!.map { relationController.create(createdCollection.id!!, it) }
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(mapper.mapDtoToResponse(createdCollection, relations))
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.mapDtoToResponse(createdCollection.copy(relations = emptyList())))
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
