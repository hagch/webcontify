package io.webcontify.backend.collections.services

import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import io.webcontify.backend.models.WebContifyCollectionDto
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.jooq.impl.SQLDataType.*
import org.springframework.stereotype.Component

@Component
class CollectionCreateTableDao(
    val dslContext: DSLContext,
    val columStrategy: ColumnHandlerStrategy
) {

  fun createTable(collection: WebContifyCollectionDto) {

    val tableBuilder = dslContext.createTable(collection.name)
    collection.columns?.forEach { column ->
      columStrategy.getHandlerFor(column.type).let {
        tableBuilder.column(column.name, it.getColumnType())
      }
    }
    val primaryKeyColums =
        collection.columns
            ?.filter { column -> column.isPrimaryKey }
            ?.map { column -> field(name(collection.name, column.name)) }
    if (primaryKeyColums.isNullOrEmpty()) {
      throw RuntimeException()
    }
    tableBuilder.constraints(constraint("PK_" + collection.name).primaryKey(primaryKeyColums))
    tableBuilder.execute()
  }
}
