package io.webcontify.backend.relations

import io.webcontify.backend.configurations.RELATIONS_PATH
import io.webcontify.backend.relations.mappers.RelationMapper
import io.webcontify.backend.relations.models.CreateRelationRequest
import io.webcontify.backend.relations.models.RelationDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class CollectionRelationController(
    private val service: RelationService,
    private val mapper: RelationMapper,
    private val relationService: RelationService
) {

  @DeleteMapping("$RELATIONS_PATH/{id}")
  fun delete(@PathVariable("id") id: Long): ResponseEntity<Unit> {
    relationService.delete(id)
    return ResponseEntity.noContent().build()
  }

  @PostMapping(RELATIONS_PATH)
  fun create(@RequestBody relation: CreateRelationRequest): ResponseEntity<RelationDto> {
    val createdRelation = service.create(mapper.mapApiToDto(relation))
    return ResponseEntity.status(HttpStatus.CREATED).body(createdRelation)
  }
}
