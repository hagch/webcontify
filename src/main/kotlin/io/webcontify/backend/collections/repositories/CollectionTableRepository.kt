package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Repository

@Repository
class CollectionTableRepository(
    val dslContext: DSLContext,
    val columStrategy: ColumnHandlerStrategy
) {

  fun create(collection: WebContifyCollectionDto) {

    val tableBuilder = dslContext.createTable(collection.name)
    collection.columns.forEach { column ->
      columStrategy.getHandlerFor(column.type).let {
        tableBuilder.column(column.name, it.getColumnType())
      }
    }
    val primaryKeyColums =
        collection.columns
            .filter { column -> column.isPrimaryKey }
            .map { column -> field(name(collection.name, column.name)) }
    if (primaryKeyColums.isEmpty()) {
      throw RuntimeException()
    }
    tableBuilder.constraints(constraint("PK_" + collection.name).primaryKey(primaryKeyColums))
    tableBuilder.execute()
  }

  fun updateName(newName: String, oldName: String) {
    if (oldName != newName) {
      dslContext.alterTableIfExists(oldName).renameTo(newName).execute()
    }
  }

  fun delete(name: String) {
    dslContext.dropTableIfExists(name).execute()
  }
}
