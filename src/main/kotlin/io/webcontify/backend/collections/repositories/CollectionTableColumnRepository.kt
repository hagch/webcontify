package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.apis.ErrorCode
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.stereotype.Repository

@Repository
class CollectionTableColumnRepository(
    val dslContext: DSLContext,
    val columStrategy: ColumnHandlerStrategy
) {

  fun create(collection: WebContifyCollectionDto, column: WebContifyCollectionColumnDto) {
    val type = columStrategy.getHandlerFor(column).getColumnType()
    collection.columns
        ?.firstOrNull { it.name == column.name }
        ?.let {
          throw AlreadyExistsException(
              ErrorCode.COLUMN_WITH_NAME_ALREADY_EXISTS,
              listOf(column.name, column.collectionId?.toString() ?: ""))
        }

    val query = dslContext.alterTable(collection.name).addColumn(field(column.name, type))
    try {
      query.execute()
    } catch (_: BadSqlGrammarException) {
      throw throw UnprocessableContentException(
          ErrorCode.UNABLE_TO_CREATE_COLUMN,
          listOf(column.name, column.collectionId?.toString() ?: ""))
    }
  }

  fun update(
      collection: WebContifyCollectionDto,
      column: WebContifyCollectionColumnDto,
      oldName: String
  ) {
    val oldColumn =
        collection.columns?.find { it.name == oldName }
            ?: throw NotFoundException(
                ErrorCode.COLUMN_NOT_FOUND, listOf(oldName, column.collectionId?.toString() ?: ""))
    if (oldColumn.type != column.type || oldColumn.isPrimaryKey != column.isPrimaryKey) {
      throw UnprocessableContentException(ErrorCode.UNSUPPORTED_COLUMN_OPERATION)
    }
    if (oldName != column.name) {
      val query = dslContext.alterTable(collection.name).renameColumn(oldName).to(column.name)
      try {
        query.execute()
      } catch (_: BadSqlGrammarException) {
        throw UnprocessableContentException(
            ErrorCode.UNABLE_TO_RENAME_COLUMN,
            listOf(oldName, column.name, column.collectionId.toString()))
      }
    }
  }

  fun delete(collection: WebContifyCollectionDto, name: String) {
    dslContext.alterTableIfExists(collection.name).dropColumnIfExists(field(name)).execute()
  }
}
