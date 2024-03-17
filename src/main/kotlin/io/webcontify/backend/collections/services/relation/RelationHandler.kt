package io.webcontify.backend.collections.services.relation

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionRelationDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionRelationType

interface RelationHandler {

  fun getType(): WebcontifyCollectionRelationType

  fun createRelation(relation: WebContifyCollectionRelationDto): WebContifyCollectionRelationDto
}
