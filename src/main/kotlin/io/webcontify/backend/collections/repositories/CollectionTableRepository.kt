package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.services.column.handler.ColumnHandlerStrategy
import org.jooq.ConstraintEnforcementStep
import org.jooq.DSLContext
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CollectionTableRepository(
    val dslContext: DSLContext,
    val columStrategy: ColumnHandlerStrategy
) {

  @Transactional
  fun create(collection: WebContifyCollectionDto) {
    val primaryKeyColums =
        collection.columns
            ?.filter { column -> column.isPrimaryKey }
            ?.map { column -> field(column.name) }
    if (primaryKeyColums.isNullOrEmpty()) {
      throw UnprocessableContentException(ErrorCode.UNABLE_TO_CREATE_TABLE)
    }
    val constraints: MutableList<ConstraintEnforcementStep> =
        mutableListOf(constraint("pk_" + collection.name).primaryKey(primaryKeyColums))
    val tableBuilder = dslContext.createTable(collection.name)
    collection.columns.forEach { column ->
      columStrategy.getHandlerFor(column).let {
        tableBuilder.column(field(column.name, it.getColumnType(column.configuration)))
        constraints.addAll(it.getColumnConstraints(column))
      }
    }
    tableBuilder.constraints(constraints)
    tableBuilder.execute()
  }

  @Transactional
  fun updateName(newName: String, oldName: String) {
    try {
      dslContext.alterTableIfExists(oldName).renameTo(newName).execute()
    } catch (_: Exception) {
      throw UnprocessableContentException(ErrorCode.UNABLE_TO_UPDATE_TABLE_NAME, oldName, newName)
    }
  }

  @Transactional
  fun delete(name: String) {
    dslContext.dropTableIfExists(name).execute()
  }
}
