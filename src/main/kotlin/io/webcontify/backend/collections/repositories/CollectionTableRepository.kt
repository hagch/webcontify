package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.apis.ErrorCode
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
    collection.columns?.forEach { column ->
      columStrategy.getHandlerFor(column).let {
        tableBuilder.column(field(column.name, it.getColumnType()))
      }
    }
    val primaryKeyColums =
        collection.columns
            ?.filter { column -> column.isPrimaryKey }
            ?.map { column -> field(column.name) }
    if (primaryKeyColums.isNullOrEmpty()) {
      throw UnprocessableContentException(ErrorCode.UNABLE_TO_CREATE_TABLE)
    }
    tableBuilder.constraints(constraint("PK_" + collection.name).primaryKey(primaryKeyColums))
    tableBuilder.execute()
  }

  fun updateName(newName: String, oldName: String) {
    try {
      dslContext.alterTableIfExists(oldName).renameTo(newName).execute()
    } catch (_: Exception) {
      throw UnprocessableContentException(
          ErrorCode.UNABLE_TO_UPDATE_TABLE_NAME, listOf(oldName, newName))
    }
  }

  fun delete(name: String) {
    dslContext.dropTableIfExists(name).execute()
  }
}
