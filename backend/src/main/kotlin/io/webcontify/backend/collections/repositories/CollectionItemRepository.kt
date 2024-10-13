package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.mappers.ItemMapper
import io.webcontify.backend.collections.models.IdentifierMap
import io.webcontify.backend.collections.models.Item
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.services.field.handler.FieldHandlerStrategy
import io.webcontify.backend.collections.utils.camelToSnakeCase
import io.webcontify.backend.collections.utils.toKeyValueString
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CollectionItemRepository(
    val dslContext: DSLContext,
    val mapper: ItemMapper,
    val fieldHandlerStrategy: FieldHandlerStrategy
) {

  @Transactional(readOnly = true)
  fun getByIdFor(collection: WebContifyCollectionDto, identifierMap: IdentifierMap): Item {
    val fields = getFieldConditionsFor(collection, identifierMap)
    try {
      return dslContext.selectFrom(collection.name.camelToSnakeCase()).where(fields).fetchOne {
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
      dslContext.deleteFrom(table(collection.name.camelToSnakeCase())).where(fields).execute()
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
    val allFields = collection.queryAbleFields().map { field(it.name.camelToSnakeCase()) }
    try {
      dslContext
          .insertInto(table(collection.name.camelToSnakeCase()), fields)
          .values(createAbleItem.values)
          .returning(allFields)
          .fetchOne()
          ?.let {
            return mapItemToResult(it.intoMap(), collection)
          }
    } catch (e: DuplicateKeyException) {
      throw AlreadyExistsException(
          ErrorCode.ITEM_ALREADY_EXISTS,
          collection.primaryFieldItemValueString(item),
          collection.id.toString())
    } catch (e: DataIntegrityViolationException) {
      throw UnprocessableContentException(
          ErrorCode.CONSTRAINT_EXCEPTION, item.toKeyValueString(), collection.id.toString())
    }
    throw UnprocessableContentException(
        ErrorCode.UNABLE_TO_CREATE_ITEM, item.toKeyValueString(), collection.id.toString())
  }

  private fun getCreateAbleItem(collection: WebContifyCollectionDto, item: Item): Item {
    val removeAblePrimaryKeys =
        collection.fields
            ?.filter {
              it.isPrimaryKey &&
                  (it.type == WebcontifyCollectionFieldType.NUMBER ||
                      it.type == WebcontifyCollectionFieldType.UUID)
            }
            ?.map {
              val isRemovable =
                  if (it.type == WebcontifyCollectionFieldType.UUID) false
                  else if (it.type == WebcontifyCollectionFieldType.NUMBER) true else false
              return@map it.name to isRemovable
            }
            ?.toMap() ?: emptyMap()
    val itemWithoutRemovablePrimaryKeys =
        item.filter {
          !(removeAblePrimaryKeys.contains(it.key) && removeAblePrimaryKeys[it.key] == true)
        }
    val mappedItem = mapItemToStore(itemWithoutRemovablePrimaryKeys, collection)
    return mappedItem
  }

  @Transactional
  fun update(collection: WebContifyCollectionDto, identifierMap: IdentifierMap, item: Item): Item {
    val query = dslContext.update(table(collection.name.camelToSnakeCase()))
    val fieldMap = mapItemToStore(item, collection).entries.associate { field(it.key) to it.value }
    val conditions = getConditions(identifierMap, collection)
    try {
      query.set(fieldMap).where(conditions).returning(fieldMap.keys).fetchOne()?.let {
        val updatedItemValues = it.intoMap().toMutableMap()
        updatedItemValues.putAll(mapper.mapKeysToDataStore(identifierMap))
        return mapItemToResult(updatedItemValues, collection).toMutableMap()
      }
    } catch (e: DataIntegrityViolationException) {
      throw UnprocessableContentException(
          ErrorCode.CONSTRAINT_EXCEPTION, item.toKeyValueString(), collection.id.toString())
    }
    throw UnprocessableContentException(
        ErrorCode.ITEM_NOT_UPDATED,
        "( ${identifierMap.toKeyValueString()}): " + item.toKeyValueString(),
        collection.id.toString())
  }

  @Transactional
  fun getAllFor(collection: WebContifyCollectionDto): List<Item> {
    val select = dslContext.selectFrom(collection.name.camelToSnakeCase())
    try {
      return select.fetch { record -> mapItemToResult(record.intoMap(), collection) }
    } catch (e: BadSqlGrammarException) {
      throw UnprocessableContentException(
          ErrorCode.UNABLE_TO_RETRIEVE_ITEMS, collection.id.toString())
    }
  }

  private fun getConditions(identifierMap: IdentifierMap, collection: WebContifyCollectionDto) =
      mapItemToStore(identifierMap, collection).map {
        condition(field("${collection.name.camelToSnakeCase()}.${it.key}").eq(it.value))
      }

  private fun getFieldConditionsFor(
      collection: WebContifyCollectionDto,
      identifierTypeMap: IdentifierMap
  ): List<Condition> {
    return mapItemToStore(identifierTypeMap, collection).map {
      field("${collection.name.camelToSnakeCase()}.${it.key}").eq(it.value)
    }
  }

  private fun mapItemToStore(item: Item, collection: WebContifyCollectionDto): Item {
    if (collection.fields
        ?.filter { it.type == WebcontifyCollectionFieldType.RELATION_MIRROR }
        ?.map { it.name }
        ?.any { item.containsKey(it) } == true) {
      throw UnprocessableContentException(ErrorCode.MIRROR_FIELD_INCLUDED)
    }
    return collection.fields?.let { fields ->
      val castedItem = fieldHandlerStrategy.castItemToJavaTypes(fields, item).toMap()
      return mapper.mapKeysToDataStore(castedItem)
    } ?: emptyMap()
  }

  private fun mapItemToResult(item: Item, collection: WebContifyCollectionDto): Item {
    val responseItem = mapper.mapKeysToResponse(item)
    return collection.fields?.let { fields ->
      return fieldHandlerStrategy.castItemToJavaTypes(fields, responseItem)
    } ?: emptyMap()
  }
}
