package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.mappers.CollectionRelationMapper
import io.webcontify.backend.collections.models.apis.WebContifyCollectionRelationApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionRelationApiUpdateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
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
      @RequestBody relation: Set<WebContifyCollectionRelationApiCreateRequest>
  ): Set<WebContifyCollectionRelationDto> {
    // TODO check if its the same relation
    return relationService.create(
        relation
            .map {
              relationMapper.mapToDto(
                  it,
                  collectionService.getById(sourceCollectionId),
                  collectionService.getById(it.referencedCollectionId))
            }
            .toSet())
  }

  @PutMapping("$COLLECTIONS_PATH/{sourceCollectionId}/relations")
  fun update(
      @PathVariable sourceCollectionId: Int,
      @RequestBody relation: Set<WebContifyCollectionRelationApiUpdateRequest>
  ): Set<WebContifyCollectionRelationApiUpdateRequest> {
    // TODO check if its the same relation
    return relationService.update(relation)
  }
}
