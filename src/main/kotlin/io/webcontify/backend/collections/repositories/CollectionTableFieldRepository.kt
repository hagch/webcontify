package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.services.field.handler.FieldHandlerStrategy
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CollectionTableFieldRepository(
    val dslContext: DSLContext,
    val columStrategy: FieldHandlerStrategy
) {

  @Transactional
  fun create(collection: WebContifyCollectionDto, field: WebContifyCollectionFieldDto) {
    val type = columStrategy.getHandlerFor(field).getFieldType() ?: return
    collection.getFieldWithName(field.name)?.let {
      throw AlreadyExistsException(
          ErrorCode.FIELD_WITH_NAME_ALREADY_EXISTS, field.name, field.collectionId.toString())
    }

    val query = dslContext.alterTable(collection.name).addColumn(field(field.name, type))
    try {
      query.execute()
    } catch (_: BadSqlGrammarException) {
      throw UnprocessableContentException(
          ErrorCode.UNABLE_TO_CREATE_FIELD, field.name, field.collectionId.toString())
    }
  }

  @Transactional
  fun update(
      collection: WebContifyCollectionDto,
      field: WebContifyCollectionFieldDto,
      oldName: String
  ) {
    val oldField =
        collection.getFieldWithName(oldName)
            ?: throw NotFoundException(
                ErrorCode.FIELD_NOT_FOUND, oldName, field.collectionId.toString())
    if (!oldField.isUpdateAble(field)) {
      throw UnprocessableContentException(ErrorCode.UNSUPPORTED_FIELD_OPERATION)
    }
    if (oldName != field.name) {
      val query = dslContext.alterTable(collection.name).renameColumn(oldName).to(field.name)
      try {
        query.execute()
      } catch (_: BadSqlGrammarException) {
        throw UnprocessableContentException(
            ErrorCode.UNABLE_TO_RENAME_FIELD, oldName, field.name, field.collectionId.toString())
      }
    }
  }

  @Transactional
  fun delete(collection: WebContifyCollectionDto, name: String) {
    dslContext.alterTableIfExists(collection.name).dropColumnIfExists(field(name)).execute()
  }
}
