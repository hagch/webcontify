package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import io.webcontify.backend.collections.utils.camelToSnakeCase
import io.webcontify.backend.collections.utils.doubleQuote
import io.webcontify.backend.collections.utils.snakeToCamelCase
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.DataType
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Repository

@Repository
class CollectionItemRepository(
    val dslContext: DSLContext,
    val columnHandlerStrategy: ColumnHandlerStrategy
) {

  fun getByIdFor(
      collection: WebContifyCollectionDto,
      identifierMap: Map<String, String?>
  ): Map<String, Any> {
    val identifierTypeMap = identifierMap.entries.toIdentifierMap(collection)
    val fields = getFieldsOf(collection, identifierTypeMap)
    val map =
        dslContext.selectFrom(collection.name.doubleQuote()).where(fields).fetchOne { record ->
          identifierTypeMap
              .map { it.key.lowercase().snakeToCamelCase() to record.getValue(it.key) }
              .toMap()
        }
            ?: mutableMapOf()
    return map
  }

  fun deleteById(collection: WebContifyCollectionDto, identifierMap: Map<String, String?>) {
    val identifierTypeMap = identifierMap.entries.toIdentifierMap(collection)
    val fields = getFieldsOf(collection, identifierTypeMap)
    dslContext.deleteFrom(table(collection.name.doubleQuote())).where(fields).execute()
  }

  fun create(collection: WebContifyCollectionDto, item: Map<String, Any>): Map<String, Any> {
    val identifierTypeMap = item.entries.toIdentifierMap(collection)
    val fields = item.keys.map { field(it.camelToSnakeCase().doubleQuote()) }
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
    dslContext
        .insertInto(table(collection.name.doubleQuote()), fields)
        .values(values)
        .execute()
        .let {
          if (it != 1) {
            throw RuntimeException()
          }
        }
    return item
  }

  fun getAllFor(collection: WebContifyCollectionDto): List<Map<String, Any>> {
    return dslContext.selectFrom(collection.name.doubleQuote()).fetch { record ->
      collection.columns.associate {
        it.name.lowercase().snakeToCamelCase() to record.getValue(it.name)
      }
    }
  }

  private fun getColumnTypeMap(
      collection: WebContifyCollectionDto
  ): Map<String, Pair<WebContifyCollectionColumnDto, DataType<*>>> {
    return collection.columns.associateBy(
        { it.name.lowercase() },
        { Pair(it, columnHandlerStrategy.getHandlerFor(it.type).getColumnType()) })
  }

  private fun Set<Map.Entry<*, *>>.toIdentifierMap(
      collection: WebContifyCollectionDto
  ): Map<String, Pair<Any?, DataType<*>?>> {
    val columnTypeMap = getColumnTypeMap(collection)
    return this.associate {
      (columnTypeMap[it.key]?.first?.name
          ?: throw RuntimeException()) to Pair(it.value, columnTypeMap[it.key]?.second)
    }
  }

  private fun getFieldsOf(
      collection: WebContifyCollectionDto,
      identifierTypeMap: Map<String, Pair<Any?, DataType<*>?>>
  ): List<Condition> {
    return identifierTypeMap
        .map {
          if (it.value.second == null) {
            field("${collection.name.doubleQuote()}.${it.key.doubleQuote()}").eq(it.value.first)
          } else {
            field("${collection.name.doubleQuote()}.${it.key.doubleQuote()}")
                .eq(cast(it.value.first, it.value.second))
          }
        }
        .toMutableList()
  }
}
