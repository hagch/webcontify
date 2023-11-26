package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CollectionTableColumnRepository(
    val dslContext: DSLContext,
    val columStrategy: ColumnHandlerStrategy
) {

  @Transactional
  fun create(collection: WebContifyCollectionDto, column: WebContifyCollectionColumnDto) {
    val type = columStrategy.getHandlerFor(column).getColumnType()
    collection.getColumnWithName(column.name)?.let {
      throw AlreadyExistsException(
          ErrorCode.COLUMN_WITH_NAME_ALREADY_EXISTS, column.name, column.collectionId.toString())
    }

    val query = dslContext.alterTable(collection.name).addColumn(field(column.name, type))
    try {
      query.execute()
    } catch (_: BadSqlGrammarException) {
      throw UnprocessableContentException(
          ErrorCode.UNABLE_TO_CREATE_COLUMN, column.name, column.collectionId.toString())
    }
  }

  @Transactional
  fun update(
      collection: WebContifyCollectionDto,
      column: WebContifyCollectionColumnDto,
      oldName: String
  ) {
    val oldColumn =
        collection.getColumnWithName(oldName)
            ?: throw NotFoundException(
                ErrorCode.COLUMN_NOT_FOUND, oldName, column.collectionId.toString())
    if (!oldColumn.isUpdateAble(column)) {
      throw UnprocessableContentException(ErrorCode.UNSUPPORTED_COLUMN_OPERATION)
    }
    if (oldName != column.name) {
      val query = dslContext.alterTable(collection.name).renameColumn(oldName).to(column.name)
      try {
        query.execute()
      } catch (_: BadSqlGrammarException) {
        throw UnprocessableContentException(
            ErrorCode.UNABLE_TO_RENAME_COLUMN, oldName, column.name, column.collectionId.toString())
      }
    }
  }

  @Transactional
  fun delete(collection: WebContifyCollectionDto, name: String) {
    dslContext.alterTableIfExists(collection.name).dropColumnIfExists(field(name)).execute()
  }
}
