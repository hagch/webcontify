package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import io.webcontify.backend.collections.utils.doubleQuote
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
    val type = columStrategy.getHandlerFor(column.type).getColumnType()
    collection.columns
        ?.firstOrNull { it.name == column.name }
        ?.let { throw AlreadyExistsException() }

    val query = dslContext.alterTable(collection.name).addColumn(field(name(column.name), type))
    try {
      query.execute()
    } catch (_: BadSqlGrammarException) {
      throw throw UnprocessableContentException()
    }
  }

  fun update(
      collection: WebContifyCollectionDto,
      column: WebContifyCollectionColumnDto,
      oldName: String
  ) {
    val columnType =
        collection.columns?.find { it.name == oldName }?.type ?: throw NotFoundException()
    if (columnType != column.type) {
      throw UnprocessableContentException()
    }
    if (oldName != column.name) {
      val query = dslContext.alterTable(collection.name).renameColumn(oldName).to(column.name)
      try {
        query.execute()
      } catch (_: BadSqlGrammarException) {
        throw UnprocessableContentException()
      }
    }
  }

  fun delete(collection: WebContifyCollectionDto, name: String) {
    dslContext
        .alterTableIfExists(collection.name)
        .dropColumnIfExists(field(name.doubleQuote()))
        .execute()
  }
}
