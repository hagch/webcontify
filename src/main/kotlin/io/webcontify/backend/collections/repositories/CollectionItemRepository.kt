package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.IdentifierMap
import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import io.webcontify.backend.collections.utils.camelToSnakeCase
import io.webcontify.backend.collections.utils.snakeToCamelCase
import io.webcontify.backend.collections.utils.toKeyValueString
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CollectionItemRepository(
    val dslContext: DSLContext,
    val columnHandlerStrategy: ColumnHandlerStrategy
) {

  @Transactional(readOnly = true)
  fun getByIdFor(collection: WebContifyCollectionDto, identifierMap: IdentifierMap): Item {
    val fields = getFieldConditionsFor(collection, identifierMap)
    try {
      return dslContext.selectFrom(collection.name).where(fields).fetchOne {
        mapItemToResult(it.intoMap(), collection)
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

  @Transactional
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

  @Transactional
  fun create(collection: WebContifyCollectionDto, item: Item): Item {
    val createAbleItem = getCreateAbleItem(collection, item)
    val fields = createAbleItem.keys.map { field(it) }
    val allFields = collection.columns?.map { field(it.name) } ?: emptyList()
    try {
      dslContext
          .insertInto(table(collection.name), fields)
          .values(createAbleItem.values)
          .returning(allFields)
          .fetchOne()
          ?.let {
            return mapItemToResult(it.intoMap(), collection)
          }
    } catch (e: DuplicateKeyException) {
      throw AlreadyExistsException(
          ErrorCode.ITEM_ALREADY_EXISTS,
          collection.primaryColumnItemValueString(item),
          collection.id.toString())
    }
    throw UnprocessableContentException(
        ErrorCode.UNABLE_TO_CREATE_ITEM, item.toKeyValueString(), collection.id.toString())
  }

  private fun getCreateAbleItem(collection: WebContifyCollectionDto, item: Item): Item {
    val removeAblePrimaryKeys =
        collection.columns
            ?.filter {
              it.isPrimaryKey &&
                  (it.type == WebcontifyCollectionColumnType.NUMBER ||
                      it.type == WebcontifyCollectionColumnType.UUID)
            }
            ?.map { it.name }
            ?.toList()
            ?: emptyList()
    val itemWithoutRemovablePrimaryKeys =
        item
            .mapKeys { it.key.camelToSnakeCase() }
            .filter { !(removeAblePrimaryKeys.contains(it.key) && it.value == null) }
    val mappedItem = mapItemToStore(itemWithoutRemovablePrimaryKeys, collection)
    return mappedItem
  }

  @Transactional
  fun update(collection: WebContifyCollectionDto, identifierMap: IdentifierMap, item: Item): Item {
    val query = dslContext.update(table(collection.name))
    val fieldMap =
        mapItemToStore(item.mapKeys { it.key.camelToSnakeCase() }, collection).entries.associate {
          field(it.key) to it.value
        }
    val conditions = getConditions(identifierMap, collection)
    query.set(fieldMap).where(conditions).returning(fieldMap.keys).fetchOne()?.let {
      val updatedItemValues = it.intoMap().toMutableMap()
      updatedItemValues.putAll(identifierMap.mapKeys { entry -> entry.key.camelToSnakeCase() })
      return mapItemToResult(updatedItemValues, collection).toMutableMap()
    }
    throw UnprocessableContentException(
        ErrorCode.ITEM_NOT_UPDATED, item.toKeyValueString(), collection.id.toString())
  }

  @Transactional
  fun getAllFor(collection: WebContifyCollectionDto): List<Item> {
    val select = dslContext.selectFrom(collection.name)
    try {
      return select.fetch { record -> mapItemToResult(record.intoMap(), collection) }
    } catch (e: BadSqlGrammarException) {
      throw UnprocessableContentException(
          ErrorCode.UNABLE_TO_RETRIEVE_ITEMS, collection.id.toString())
    }
  }

  private fun getConditions(identifierMap: IdentifierMap, collection: WebContifyCollectionDto) =
      mapItemToStore(identifierMap, collection).map {
        condition(field("${collection.name}.${it.key.camelToSnakeCase()}").eq(it.value))
      }

  private fun getFieldConditionsFor(
      collection: WebContifyCollectionDto,
      identifierTypeMap: IdentifierMap
  ): List<Condition> {
    return mapItemToStore(identifierTypeMap, collection).map {
      field("${collection.name}.${it.key.camelToSnakeCase()}").eq(it.value)
    }
  }

  private fun mapItemToStore(item: Item, collection: WebContifyCollectionDto): Item {
    return collection.columns?.let { columns ->
      return columnHandlerStrategy.castItemToJavaTypes(columns, item).toMap()
    }
        ?: emptyMap()
  }

  private fun mapItemToResult(item: Item, collection: WebContifyCollectionDto): Item {
    return collection.columns?.let { columns ->
      return columnHandlerStrategy
          .castItemToJavaTypes(columns, item)
          .map { it.key.snakeToCamelCase() to it.value }
          .toMap()
    }
        ?: emptyMap()
  }
}
