package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.IdentifierMap
import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.models.apis.ErrorCode
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import io.webcontify.backend.collections.utils.camelToSnakeCase
import io.webcontify.backend.collections.utils.snakeToCamelCase
import io.webcontify.backend.collections.utils.toKeyValueString
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL.*
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.stereotype.Repository

@Repository
class CollectionItemRepository(
    val dslContext: DSLContext,
    val columnHandlerStrategy: ColumnHandlerStrategy
) {

  fun getByIdFor(collection: WebContifyCollectionDto, identifierMap: IdentifierMap): Item {
    val fields = getFieldConditionsFor(collection, identifierMap)
    try {
      return dslContext.selectFrom(collection.name).where(fields).fetchOne {
        mapRecordToItem(collection, it)
      }
          ?: throw NotFoundException(
              ErrorCode.ITEM_NOT_FOUND, identifierMap.toKeyValueString(), collection.id.toString())
    } catch (e: BadSqlGrammarException) {
      throw UnprocessableContentException(
          ErrorCode.UNABLE_TO_RETRIEVE_ITEM,
          identifierMap.toKeyValueString(),
          collection.id.toString())
    }
  }

  fun deleteById(collection: WebContifyCollectionDto, identifierMap: IdentifierMap) {
    val fields = getFieldConditionsFor(collection, identifierMap)
    try {
      dslContext.deleteFrom(table(collection.name)).where(fields).execute()
    } catch (e: BadSqlGrammarException) {
      throw UnprocessableContentException(
          ErrorCode.UNABLE_TO_DELETE_ITEM,
          identifierMap.toKeyValueString(),
          collection.id.toString())
    }
  }

  fun create(collection: WebContifyCollectionDto, item: Item): Item {
    val fields = item.keys.map { field(it.camelToSnakeCase()) }
    val values = mapItem(item, collection).values
    try {
      dslContext.insertInto(table(collection.name), fields).values(values).execute().let { isCreated
        ->
        if (isCreated != 1) {
          throw UnprocessableContentException(
              ErrorCode.UNABLE_TO_CREATE_ITEM, item.toKeyValueString(), collection.id.toString())
        }
      }
    } catch (e: DuplicateKeyException) {
      throw AlreadyExistsException(
          ErrorCode.ITEM_ALREADY_EXISTS,
          collection.primaryColumnItemValueString(item),
          collection.id.toString())
    }
    return item
  }

  fun update(collection: WebContifyCollectionDto, identifierMap: IdentifierMap, item: Item): Item {
    val query = dslContext.update(table(collection.name))
    val fieldMap = item.entries.associate { field(it.key.camelToSnakeCase()) to it.value }
    val conditions = getConditions(identifierMap, collection)
    query.set(fieldMap).where(conditions).execute().let { isUpdated ->
      if (isUpdated != 1) {
        throw UnprocessableContentException(
            ErrorCode.ITEM_NOT_UPDATED, item.toKeyValueString(), collection.id.toString())
      }
    }
    val itemWithIdentifiers = identifierMap.toMutableMap()
    itemWithIdentifiers.putAll(item)
    return mapItem(itemWithIdentifiers, collection)
  }

  fun getAllFor(collection: WebContifyCollectionDto): List<Item> {
    val select = dslContext.selectFrom(collection.name)
    try {
      return select.fetch { record ->
        collection.columns?.associate { it.name.snakeToCamelCase() to record.getValue(it.name) }
      }
    } catch (e: BadSqlGrammarException) {
      throw UnprocessableContentException(
          ErrorCode.UNABLE_TO_RETRIEVE_ITEMS, collection.id.toString())
    }
  }

  private fun mapRecordToItem(collection: WebContifyCollectionDto, record: Record) =
      (collection.columns?.associate { it.name.snakeToCamelCase() to record.getValue(it.name) }
          ?: mapOf())

  private fun getConditions(identifierMap: IdentifierMap, collection: WebContifyCollectionDto) =
      mapItem(identifierMap, collection).map {
        condition(field("${collection.name}.${it.key.camelToSnakeCase()}").eq(it.value))
      }

  private fun getFieldConditionsFor(
      collection: WebContifyCollectionDto,
      identifierTypeMap: IdentifierMap
  ): List<Condition> {
    return mapItem(identifierTypeMap, collection).map {
      field("${collection.name}.${it.key}").eq(it.value)
    }
  }

  private fun mapItem(item: Item, collection: WebContifyCollectionDto): Item {
    return collection.columns?.let { columns ->
      return columnHandlerStrategy
          .castItemToJavaTypes(columns, item)
          .map { it.key.camelToSnakeCase() to it.value }
          .toMap()
    }
        ?: emptyMap()
  }
}
