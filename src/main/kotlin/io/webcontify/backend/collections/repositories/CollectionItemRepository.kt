package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
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
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.stereotype.Repository

@Repository
class CollectionItemRepository(
    val dslContext: DSLContext,
    val columnHandlerStrategy: ColumnHandlerStrategy
) {

  fun getByIdFor(
      collection: WebContifyCollectionDto,
      identifierMap: Map<String, Any?>
  ): Map<String, Any> {
    val identifierTypeMap = identifierMap.entries.toIdentifierMap(collection)
    val fields = getFieldsOf(collection, identifierTypeMap)
    val map =
        dslContext.selectFrom(collection.name.doubleQuote()).where(fields).fetchOne { record ->
          identifierTypeMap.map { it.key.snakeToCamelCase() to record.getValue(it.key) }.toMap()
        }
            ?: throw NotFoundException()
    return map
  }

  fun deleteById(collection: WebContifyCollectionDto, identifierMap: Map<String, Any?>) {
    val identifierTypeMap = identifierMap.entries.toIdentifierMap(collection)
    val fields = getFieldsOf(collection, identifierTypeMap)
    try {
      dslContext.deleteFrom(table(collection.name.doubleQuote())).where(fields).execute()
    } catch (e: BadSqlGrammarException) {
      throw UnprocessableContentException()
    }
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
    try {
      dslContext
          .insertInto(table(collection.name.doubleQuote()), fields)
          .values(values)
          .execute()
          .let {
            if (it != 1) {
              throw UnprocessableContentException()
            }
          }
    } catch (e: DuplicateKeyException) {
      throw AlreadyExistsException()
    }
    return item
  }

  fun getAllFor(collection: WebContifyCollectionDto): List<Map<String, Any>> {
    var select = dslContext.selectFrom(collection.name.doubleQuote())
    try {
      return select.fetch { record ->
        collection.columns?.associate { it.name.snakeToCamelCase() to record.getValue(it.name) }
      }
    } catch (e: BadSqlGrammarException) {
      throw UnprocessableContentException()
    }
  }

  private fun getColumnTypeMap(
      collection: WebContifyCollectionDto
  ): Map<String, Pair<WebContifyCollectionColumnDto, DataType<*>>>? {
    return collection.columns?.associateBy(
        { it.name.snakeToCamelCase() },
        { Pair(it, columnHandlerStrategy.getHandlerFor(it.type).getColumnType()) })
  }

  private fun Set<Map.Entry<*, *>>.toIdentifierMap(
      collection: WebContifyCollectionDto
  ): Map<String, Pair<Any?, DataType<*>?>> {
    val columnTypeMap = getColumnTypeMap(collection)
    return this.associate {
      (columnTypeMap?.get(it.key)?.first?.name
          ?: throw UnprocessableContentException()) to Pair(it.value, columnTypeMap[it.key]?.second)
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
