package io.webcontify.backend.views

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.utils.snakeToCamelCase
import io.webcontify.backend.jooq.enums.WebcontifyViewAggregationType
import io.webcontify.backend.relations.RelationFieldMapping
import org.jooq.*
import org.jooq.impl.DSL.*

data class RelationTree(
    val fromCollection: WebContifyCollectionDto,
    val referencedCollectionRelationName: String,
    val relationId: Long?,
    val aggregationType: WebcontifyViewAggregationType?,
    val toChildren: List<RelationTree>,
    val isMandatory: Boolean,
    val fieldMapping: Set<RelationFieldMapping>
) {

  fun getQuery(dslContext: DSLContext): SelectHavingStep<Record> {
    val select =
        dslContext
            .select(getFieldNames(null))
            .from(table(name(fromCollection.name)).`as`(getName(null)))
    appendJoin(select, this, getName(null))
    return select
  }

  private fun getName(prefix: String?): String {
    if (prefix != null) {
      return "${prefix}_${fromCollection.id.toString()}"
    }
    return fromCollection.id.toString()
  }

  private fun getFieldNamesOfCollection(
      prefix: String?,
      collection: WebContifyCollectionDto
  ): List<Field<Any>> {
    return collection.fields?.map {
      field(name(getName(prefix), it.name)).`as`(getName(prefix) + it.name.snakeToCamelCase())
    } ?: emptyList()
  }

  private fun getFieldNames(prefix: String?): List<Field<Any>> {
    var names = getFieldNamesOfCollection(prefix, fromCollection).toMutableList()
    val childrenNames =
        toChildren.map { relationTree -> relationTree.getFieldNames(getName(prefix)) }.flatten()
    names.addAll(childrenNames)
    return names
  }

  private fun appendJoin(
      select: SelectJoinStep<Record>,
      previousRelation: RelationTree,
      prefix: String?
  ) {
    for (relation in previousRelation.toChildren) {
      val mappings = relation.getFieldMappings(prefix, previousRelation).map { it.key.eq(it.value) }
      val join =
          if (relation.isMandatory) {
            select
                .innerJoin(table(name(relation.fromCollection.name)).`as`(relation.getName(prefix)))
                .on(*mappings.toTypedArray())
          } else {
            select
                .leftJoin(table(name(relation.fromCollection.name)).`as`(relation.getName(prefix)))
                .on(*mappings.toTypedArray())
          }
      appendJoin(join, relation, relation.getName(prefix))
    }
  }

  private fun getFieldMappings(
      sourcePrefix: String?,
      relation: RelationTree
  ): Map<Field<Any>, Field<Any>> {
    return fieldMapping
        .mapNotNull {
          val sourceField =
              relation.fromCollection.fields
                  ?.firstOrNull { field -> field.id == it.sourceFieldId }
                  ?.name ?: ""
          val referencedField =
              fromCollection.fields?.firstOrNull { field -> field.id == it.referencedFieldId }?.name
                  ?: ""
          if (sourceField.isEmpty() || referencedField.isEmpty()) {
            return@mapNotNull null
          }
          return@mapNotNull field(name(sourcePrefix, sourceField)) to
              field(name(getName(sourcePrefix), referencedField))
        }
        .toMap()
  }
}
