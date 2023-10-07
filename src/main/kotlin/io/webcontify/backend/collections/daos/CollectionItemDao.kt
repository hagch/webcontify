package io.webcontify.backend.collections.daos

import io.webcontify.backend.collections.models.WebContifyCollectionDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import org.jooq.DSLContext
import org.jooq.DataType
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Component

@Component
class CollectionItemDao(
    val dslContext: DSLContext,
    val columnHandlerStrategy: ColumnHandlerStrategy
) {

  fun getItemByIdFor(
      collection: WebContifyCollectionDto,
      identifierMap: Map<String, String?>
  ): Map<String, Any> {
    val columnTypeMap = getColumnTypeMap(collection)
    val identifierTypeMap =
        identifierMap.entries.associate { it.key to Pair(it.value, columnTypeMap[it.key]) }
    val fields =
        identifierTypeMap
            .map {
              if (it.value.second == null) {
                field("${collection.name}.${it.key}").eq(it.value.first)
              } else {
                field("${collection.name}.${it.key}").eq(cast(it.value.first, it.value.second))
              }
            }
            .toMutableList()
    val map =
        dslContext.selectFrom(collection.name).where(fields).fetchOne { record ->
          identifierTypeMap.map { it.key to record.getValue(it.key) }.toMap()
        }
            ?: mutableMapOf()
    return map
  }

  fun create(collection: WebContifyCollectionDto, item: Map<String, Any>): Map<String, Any> {
    val columnTypeMap = getColumnTypeMap(collection)
    val identifierTypeMap =
        item.entries.associate { it.key to Pair(it.value, columnTypeMap[it.key]) }
    val fields = item.keys.map { field("\"${it}\"") }
    val values =
        identifierTypeMap
            .map {
              if (it.value.second == null) {
                it.value
              } else {
                cast(it.value.first, it.value.second)
              }
            }
            .toMutableList()
    dslContext.insertInto(table(collection.name), fields).values(values).execute().let {
      if (it != 1) {
        throw RuntimeException()
      }
    }
    return item
  }

  private fun getColumnTypeMap(collection: WebContifyCollectionDto): Map<String, DataType<*>> {
    return collection.columns?.associateBy(
        { it.name }, { columnHandlerStrategy.getHandlerFor(it.type).getColumnType() })
        ?: mapOf()
  }
}
