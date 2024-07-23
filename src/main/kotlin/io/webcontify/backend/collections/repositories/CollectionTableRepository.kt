package io.webcontify.backend.collections.repositories

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.errors.ErrorCode
import io.webcontify.backend.collections.services.field.handler.FieldHandlerStrategy
import io.webcontify.backend.collections.utils.camelToSnakeCase
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
            ?.map { field -> field(field.name.camelToSnakeCase()) }
    if (primaryKeyColums.isNullOrEmpty()) {
      throw UnprocessableContentException(ErrorCode.UNABLE_TO_CREATE_COLLECTION)
    }
    val constraints: MutableList<ConstraintEnforcementStep> =
        mutableListOf(
            constraint("pk_" + collection.name.camelToSnakeCase()).primaryKey(primaryKeyColums))
    val tableBuilder = dslContext.createTable(collection.name.camelToSnakeCase())
    collection.fields.forEach { field ->
      val handler = columStrategy.getHandlerFor(field)
      handler
          .getFieldType(field.copy(name = field.name.camelToSnakeCase()), enableFieldAutoGeneration)
          ?.let {
            tableBuilder.column(field(field.name.camelToSnakeCase(), it))
            constraints.addAll(
                handler.getFieldConstraints(
                    field.copy(name = field.name.camelToSnakeCase()),
                    collection.name.camelToSnakeCase()))
          }
    }
    tableBuilder.constraints(constraints)
    tableBuilder.execute()
  }

  @Transactional
  fun updateName(newName: String, oldName: String) {
    val newNameSnake = newName.camelToSnakeCase()
    val oldNameSnake = oldName.camelToSnakeCase()
    try {
      dslContext.alterTableIfExists(oldNameSnake).renameTo(newNameSnake).execute()
    } catch (_: Exception) {
      throw UnprocessableContentException(ErrorCode.UNABLE_TO_UPDATE_TABLE_NAME, oldName, newName)
    }
  }

  @Transactional
  fun delete(name: String) {
    dslContext.dropTableIfExists(name.camelToSnakeCase()).execute()
  }
}
