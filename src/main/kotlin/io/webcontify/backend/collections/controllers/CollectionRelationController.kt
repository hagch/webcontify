package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.mappers.CollectionRelationMapper
import io.webcontify.backend.collections.models.apis.WebContifyCollectionRelationApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionRelationApiResponse
import io.webcontify.backend.collections.services.CollectionRelationService
import io.webcontify.backend.collections.services.CollectionService
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import org.springframework.web.bind.annotation.*

@RestController
class CollectionRelationController(
    val relationMapper: CollectionRelationMapper,
    val relationService: CollectionRelationService,
    val collectionService: CollectionService
) {

  @DeleteMapping("$COLLECTIONS_PATH/{sourceCollectionId}/relations/{name}")
  fun delete(@PathVariable sourceCollectionId: Int, @PathVariable name: String) {
    return relationService.delete(sourceCollectionId, name)
  }

  @PostMapping("$COLLECTIONS_PATH/{sourceCollectionId}/relations")
  fun create(
      @PathVariable sourceCollectionId: Int,
      @RequestBody relation: WebContifyCollectionRelationApiCreateRequest
  ): WebContifyCollectionRelationApiResponse {
    val sourceCollection = collectionService.getById(sourceCollectionId)
    val referencedCollection = collectionService.getById(relation.referencedCollectionId)
    // TODO check if its the same relation
    return relationService
        .create(relationMapper.mapToDto(relation, sourceCollection, referencedCollection))
        .let { relationMapper.mapToResponse(it) }
  }
}
