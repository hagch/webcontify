package io.webcontify.backend.relations

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldRelationMirrorConfigurationDto
import io.webcontify.backend.collections.repositories.CollectionFieldRepository
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import io.webcontify.backend.jooq.tables.references.WEBCONTIFY_COLLECTION_FIELD
import org.jooq.DSLContext
import org.jooq.impl.DSL.jsonbGetAttribute
import org.springframework.stereotype.Component

@Component
class MirrorFieldService(
    private val fieldRepository: CollectionFieldRepository,
    private val dslContext: DSLContext
) {

  fun mustBeEmpty(mirrorFields: Set<MirrorRelationFieldMapping>?) {
    mirrorFields?.let {
      if (it.isNotEmpty()) {
        throw RuntimeException("No mirror fields for source collection allowed")
      }
    }
  }

  fun canBeEmpty(
      mirrorFields: Set<MirrorRelationFieldMapping>?,
      fieldsMapping: Set<RelationFieldMapping>
  ) {
    mirrorFields?.let {
      if (it.isEmpty()) {
        return@let
      }
      val allFieldsContained =
          it.all { field ->
            fieldsMapping.any { mapping -> mapping.sourceFieldId == field.referencedFieldId }
          }
      if (it.size != fieldsMapping.size || !allFieldsContained) {
        throw RuntimeException("Not all fields for mirror covered")
      }
    }
  }

  fun create(
      collectionRelationMapping: CollectionRelationMapping,
      relationId: Long
  ): Set<RelationMirrorField> {
    val mirrorFields: MutableSet<RelationMirrorField> = mutableSetOf()
    if (collectionRelationMapping.mirrorFields.isNullOrEmpty()) {
      return mirrorFields
    }
    for (mirrorRelationFieldMapping in collectionRelationMapping.mirrorFields) {
      val referencedField = fieldRepository.getById(mirrorRelationFieldMapping.referencedFieldId)
      val mirrorField =
          fieldRepository.create(
              WebContifyCollectionFieldDto(
                  id = null,
                  isPrimaryKey = false,
                  type = WebcontifyCollectionFieldType.RELATION_MIRROR,
                  collectionId = collectionRelationMapping.id,
                  name = mirrorRelationFieldMapping.name,
                  displayName = mirrorRelationFieldMapping.name,
                  configuration =
                      WebContifyCollectionFieldRelationMirrorConfigurationDto(
                          relationId = relationId,
                          referencedField = referencedField.id!!,
                          null,
                          null,
                          null,
                          null)))
      mirrorFields.add(
          RelationMirrorField(
              collectionId = mirrorField.collectionId!!, fieldId = mirrorField.id!!))
    }
    return mirrorFields
  }

  fun deleteForRelation(relationId: Long) {
    dslContext
        .deleteFrom(WEBCONTIFY_COLLECTION_FIELD)
        .where(
            WEBCONTIFY_COLLECTION_FIELD.TYPE.eq(WebcontifyCollectionFieldType.RELATION_MIRROR)
                .and(
                    jsonbGetAttribute(WEBCONTIFY_COLLECTION_FIELD.CONFIGURATION, "relationId")
                        .cast(Long::class.java)
                        .eq(relationId)))
        .execute()
  }
}
