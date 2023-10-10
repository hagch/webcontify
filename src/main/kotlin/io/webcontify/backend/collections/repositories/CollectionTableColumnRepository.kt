package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import io.webcontify.backend.collections.utils.doubleQuote
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Repository

@Repository
class CollectionTableColumnRepository(
    val dslContext: DSLContext,
    val columStrategy: ColumnHandlerStrategy
) {

  fun create(collection: WebContifyCollectionDto, column: WebContifyCollectionColumnDto) {
    val type = columStrategy.getHandlerFor(column.type).getColumnType()
    dslContext.alterTable(collection.name).addColumn(field(name(column.name), type)).execute()
  }

  fun update(
      collection: WebContifyCollectionDto,
      column: WebContifyCollectionColumnDto,
      oldName: String
  ) {
    if (oldName != column.name) {
      dslContext.alterTable(collection.name).renameColumn(oldName).to(column.name).execute()
    }
    val columnType =
        collection.columns?.find { it.name == oldName }?.type ?: throw RuntimeException()
    if (columnType != column.type) {
      throw RuntimeException("Update column type not supported")
      /*val type = columStrategy.getHandlerFor(column.type).getColumnType()
      dslContext.alterTable(collection.name).alterColumn(column.name).set(type).execute() //casting missing */
    }
  }

  fun delete(collection: WebContifyCollectionDto, name: String) {
    dslContext.alterTable(collection.name).dropColumn(field(name.doubleQuote())).execute()
  }
}
