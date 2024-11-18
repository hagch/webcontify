package io.webcontify.backend.queries

import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.utils.camelToSnakeCase
import io.webcontify.backend.queries.mappers.QueryResultMapper
import io.webcontify.backend.queries.models.QueryRelationInfoTree
import org.jooq.*
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Service

@Service
class QueryExecutor(
    private val dslContext: DSLContext,
    private val resultMapper: QueryResultMapper
) {

  fun getAllForView(queryRelationInfoTree: QueryRelationInfoTree): List<Item> {
    val results = getQuery(queryRelationInfoTree).fetchMaps()
    return resultMapper.mapToItems(results, queryRelationInfoTree)
  }

  private fun getQuery(queryRelationInfoTree: QueryRelationInfoTree): SelectHavingStep<Record> {
    val select =
        dslContext
            .select(getFieldNames(null, queryRelationInfoTree))
            .from(
                table(name(queryRelationInfoTree.collection.name.camelToSnakeCase()))
                    .`as`(queryRelationInfoTree.getName(null)))
    appendJoin(select, queryRelationInfoTree, queryRelationInfoTree.getName(null))
    return select
  }

  private fun getFieldNamesOfCollection(
      prefix: String?,
      queryRelationInfoTree: QueryRelationInfoTree
  ): List<Field<Any>> {
    return queryRelationInfoTree.collection.fields?.map {
      field(name(queryRelationInfoTree.getName(prefix), it.name.camelToSnakeCase()))
          .`as`(queryRelationInfoTree.getName(prefix) + it.name)
    } ?: emptyList()
  }

  private fun getFieldNames(
      prefix: String?,
      queryRelationInfoTree: QueryRelationInfoTree
  ): List<Field<Any>> {
    val names = getFieldNamesOfCollection(prefix, queryRelationInfoTree).toMutableList()
    val childrenNames =
        queryRelationInfoTree.childRelationTrees
            .map { relationTree ->
              getFieldNames(queryRelationInfoTree.getName(prefix), relationTree)
            }
            .flatten()
    names.addAll(childrenNames)
    return names
  }

  private fun appendJoin(
      select: SelectJoinStep<Record>,
      previousRelation: QueryRelationInfoTree,
      prefix: String?
  ) {
    for (relation in previousRelation.childRelationTrees) {
      val mappings =
          getFieldMappings(prefix, relation, previousRelation).map { it.key.eq(it.value) }
      val join =
          if (relation.mandatory) {
            select
                .innerJoin(
                    table(name(relation.collection.name.camelToSnakeCase()))
                        .`as`(relation.getName(prefix)))
                .on(*mappings.toTypedArray())
          } else {
            select
                .leftJoin(
                    table(name(relation.collection.name.camelToSnakeCase()))
                        .`as`(relation.getName(prefix)))
                .on(*mappings.toTypedArray())
          }
      appendJoin(join, relation, relation.getName(prefix))
    }
  }

  private fun getFieldMappings(
      sourcePrefix: String?,
      currentRelationInfoTree: QueryRelationInfoTree,
      previousRelation: QueryRelationInfoTree
  ): Map<Field<Any>, Field<Any>> {
    return currentRelationInfoTree.fieldMappings
        .mapNotNull {
          val sourceField =
              previousRelation.collection.fields
                  ?.firstOrNull { field -> field.id == it.sourceFieldId }
                  ?.name
                  ?.camelToSnakeCase() ?: ""
          val referencedField =
              currentRelationInfoTree.collection.fields
                  ?.firstOrNull { field -> field.id == it.referencedFieldId }
                  ?.name
                  ?.camelToSnakeCase() ?: ""
          if (sourceField.isEmpty() || referencedField.isEmpty()) {
            return@mapNotNull null
          }
          return@mapNotNull field(name(sourcePrefix, sourceField)) to
              field(name(currentRelationInfoTree.getName(sourcePrefix), referencedField))
        }
        .toMap()
  }
}
