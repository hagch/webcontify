package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import io.webcontify.backend.collections.utils.camelToSnakeCase
import io.webcontify.backend.collections.utils.snakeToCamelCase
import org.jooq.Condition
import org.jooq.DSLContext
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
    val fields = getConditionsFor(collection, identifierMap)
    try {
      return dslContext.selectFrom(collection.name).where(fields).fetchOne { record ->
        collection.columns?.associate { it.name.snakeToCamelCase() to record.getValue(it.name) }
            ?: mapOf()
      }
          ?: throw NotFoundException()
    } catch (e: BadSqlGrammarException) {
      throw UnprocessableContentException()
    }
  }

  fun deleteById(collection: WebContifyCollectionDto, identifierMap: Map<String, Any?>) {
    val fields = getConditionsFor(collection, identifierMap)
    try {
      dslContext.deleteFrom(table(collection.name)).where(fields).execute()
    } catch (e: BadSqlGrammarException) {
      throw UnprocessableContentException()
    }
  }

  fun create(collection: WebContifyCollectionDto, item: Map<String, Any?>): Map<String, Any?> {
    val fields = item.keys.map { field(it.camelToSnakeCase()) }
    val values = mapItem(item, collection).values
    try {
      dslContext.insertInto(table(collection.name), fields).values(values).execute().let {
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
    val query = dslContext.update(table(collection.name))
    val fieldMap = item.entries.associate { field(it.key.camelToSnakeCase()) to it.value }
    val conditions =
        mapItem(identifierMap, collection).map {
          condition(field("${collection.name}.${it.key.camelToSnakeCase()}").eq(it.value))
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
    var select = dslContext.selectFrom(collection.name)
    try {
      return select.fetch { record ->
        collection.columns?.associate { it.name.snakeToCamelCase() to record.getValue(it.name) }
      }
    } catch (e: BadSqlGrammarException) {
      throw UnprocessableContentException()
    }
  }

  private fun getConditionsFor(
      collection: WebContifyCollectionDto,
      identifierTypeMap: Map<String, Any?>
  ): List<Condition> {
    return mapItem(identifierTypeMap, collection).map {
      field("${collection.name}.${it.key}").eq(it.value)
    }
  }

  private fun mapItem(
      item: Map<String, Any?>,
      collection: WebContifyCollectionDto
  ): Map<String, Any?> {
    return collection.columns?.let { columns ->
      return columnHandlerStrategy
          .castItemToJavaTypes(columns, item)
          .map { it.key.camelToSnakeCase() to it.value }
          .toMap()
    }
        ?: throw UnprocessableContentException()
  }
}
