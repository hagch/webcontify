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
          collection.columns?.associate { it.name.snakeToCamelCase() to record.getValue(it.name) }
              ?: mapOf()
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

  fun create(collection: WebContifyCollectionDto, item: Map<String, Any?>): Map<String, Any?> {
    val identifierTypeMap = item.entries.toIdentifierMap(collection)
    val fields = item.keys.map { field(it.camelToSnakeCase().doubleQuote()) }
    // TODO change with casting from java implementation
    val values =
        identifierTypeMap
            .map {
              if (it.value.second == null) {
                it.value.first
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

  fun update(
      collection: WebContifyCollectionDto,
      identifierMap: Map<String, Any?>,
      item: Map<String, Any?>
  ): Map<String, Any?> {
    val query = dslContext.update(table(collection.name.doubleQuote()))
    val identifierTypeMap = identifierMap.entries.toIdentifierMap(collection)
    val fieldMap =
        item.entries.associate { field(it.key.camelToSnakeCase().doubleQuote()) to it.value }
    // TODO change with casting from java implementation
    val conditions =
        identifierTypeMap.map {
          val value =
              if (it.value.second == null) {
                it.value.first
              } else {
                cast(it.value.first, it.value.second)
              }
          return@map condition(
              field("${collection.name.doubleQuote()}.${it.key.doubleQuote()}").eq(value))
        }
    query.set(fieldMap).where(conditions).execute().let {
      if (it != 1) {
        throw UnprocessableContentException()
      }
    }
    val itemWithIdentifiers = identifierMap.toMutableMap()
    itemWithIdentifiers.putAll(item)
    return mapItem(itemWithIdentifiers, collection)
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
        { it.name.snakeToCamelCase().lowercase() },
        { Pair(it, columnHandlerStrategy.getHandlerFor(it.type).getColumnType()) })
  }

  private fun Set<Map.Entry<String, *>>.toIdentifierMap(
      collection: WebContifyCollectionDto
  ): Map<String, Pair<Any?, DataType<*>?>> {
    val columnTypeMap = getColumnTypeMap(collection)
    return this.associate {
      (columnTypeMap?.get(it.key.lowercase())?.first?.name
          ?: throw UnprocessableContentException()) to Pair(it.value, columnTypeMap[it.key]?.second)
    }
  }

  private fun getFieldsOf(
      collection: WebContifyCollectionDto,
      identifierTypeMap: Map<String, Pair<Any?, DataType<*>?>>
  ): List<Condition> {
    // TODO change with casting from java implementation
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

  private fun mapItem(
      item: Map<String, Any?>,
      collection: WebContifyCollectionDto
  ): Map<String, Any?> {
    return collection.columns?.let {
      return@let columnHandlerStrategy.castItemToJavaTypes(it, item)
    }
        ?: throw UnprocessableContentException()
  }
}
