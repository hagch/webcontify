package io.webcontify.backend.collections.services

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import io.webcontify.backend.models.WebContifyCollectionDto
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.jooq.impl.SQLDataType.*
import org.springframework.stereotype.Component

@Component
class CollectionCreateTableDao(val dslContext: DSLContext) {

  fun createTable(collection: WebContifyCollectionDto) {

    val tableBuilder = dslContext.createTable(collection.name)
    collection.columns?.forEach {
      when (it.type) {
        WebcontifyCollectionColumnType.BOOLEAN -> tableBuilder.column(it.name, BOOLEAN)
        WebcontifyCollectionColumnType.NUMBER -> tableBuilder.column(it.name, BIGINT)
        WebcontifyCollectionColumnType.DECIMAL -> tableBuilder.column(it.name, DECIMAL)
        WebcontifyCollectionColumnType.SHORT_TEXT -> tableBuilder.column(it.name, VARCHAR)
        WebcontifyCollectionColumnType.LONG_TEXT -> tableBuilder.column(it.name, LONGVARCHAR)
        WebcontifyCollectionColumnType.TIMESTAMP -> tableBuilder.column(it.name, TIMESTAMP)
        WebcontifyCollectionColumnType.CURRENCY -> tableBuilder.column(it.name, DECIMAL)
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
