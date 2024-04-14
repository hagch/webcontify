package io.webcontify.backend.collections.controllers

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.mappers.CollectionFieldMapper
import io.webcontify.backend.collections.models.apis.WebContifyCollectionFieldApiCreateRequest
import io.webcontify.backend.collections.models.apis.WebContifyCollectionFieldApiUpdateRequest
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.services.CollectionFieldService
import io.webcontify.backend.configurations.COLLECTIONS_PATH
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class CollectionFieldController(
    val service: CollectionFieldService,
    val mapper: CollectionFieldMapper
) {

  // TODO check if changing to id
  @DeleteMapping("$COLLECTIONS_PATH/{collectionId}/fields/{name}")
  fun delete(
      @PathVariable("collectionId") collectionId: Long,
      @PathVariable("name") name: String
  ): ResponseEntity<Void> {
    service.deleteById(collectionId, name.lowercase())
    return ResponseEntity.noContent().build()
  }

  @GetMapping("$COLLECTIONS_PATH/{collectionId}/fields/{name}")
  fun getById(
      @PathVariable("collectionId") collectionId: Long,
      @PathVariable("name") name: String
  ): WebContifyCollectionFieldDto {
    return service.getById(collectionId, name.lowercase())
  }

  @GetMapping("$COLLECTIONS_PATH/{collectionId}/fields")
  fun getAllForCollection(
      @PathVariable("collectionId") collectionId: Long
  ): Set<WebContifyCollectionFieldDto> {
    return service.getAllForCollection(collectionId)
  }

  @PostMapping("$COLLECTIONS_PATH/{collectionId}/fields")
  fun create(
      @PathVariable collectionId: Long,
      @RequestBody @Valid field: WebContifyCollectionFieldApiCreateRequest
  ): WebContifyCollectionFieldDto {
    if (field.isPrimaryKey) {
      throw UnprocessableContentException(ErrorCode.UNSUPPORTED_FIELD_OPERATION)
    }
    return service.create(mapper.mapApiToDto(field, collectionId))
  }

  @PutMapping("$COLLECTIONS_PATH/{collectionId}/fields/{name}")
  fun update(
      @PathVariable("collectionId") collectionId: Long,
      @PathVariable("name") oldName: String,
      @RequestBody @Valid newField: WebContifyCollectionFieldApiUpdateRequest
  ): WebContifyCollectionFieldDto {
    return service.update(oldName.lowercase(), mapper.mapApiToDto(newField, collectionId))
  }
}
