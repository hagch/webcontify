package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.services.field.handler.FieldHandlerStrategy
import org.jooq.ConstraintEnforcementStep
import org.jooq.DSLContext
import org.jooq.impl.DSL.constraint
import org.jooq.impl.DSL.field
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CollectionTableRepository(
    val dslContext: DSLContext,
    val columStrategy: FieldHandlerStrategy
) {

  @Transactional
  fun create(collection: WebContifyCollectionDto, enableFieldAutoGeneration: Boolean = true) {
    val primaryKeyColums =
        collection.fields
            ?.filter { field -> field.isPrimaryKey }
            ?.map { field -> field(field.name) }
    if (primaryKeyColums.isNullOrEmpty()) {
      throw UnprocessableContentException(ErrorCode.UNABLE_TO_CREATE_COLLECTION)
    }
    val constraints: MutableList<ConstraintEnforcementStep> =
        mutableListOf(constraint("pk_" + collection.name).primaryKey(primaryKeyColums))
    val tableBuilder = dslContext.createTable(collection.name)
    collection.fields.forEach { field ->
      val handler = columStrategy.getHandlerFor(field)
      handler.getFieldType(field, enableFieldAutoGeneration)?.let {
        tableBuilder.column(field(field.name, it))
        constraints.addAll(handler.getFieldConstraints(field, collection.name))
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
