package io.webcontify.backend.collections.services.column.handler

import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnConfigurationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.DSL.constraint
import org.jooq.impl.DSL.field
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter

interface ColumnHandler {
  fun getColumnType(): DataType<*>

  fun getColumnHandlerType(): WebcontifyCollectionColumnType

  fun getColumnType(configuration: WebContifyCollectionColumnConfigurationDto?): DataType<*> {
    val type = getColumnType()
    return configuration?.nullable?.let {
      return@let type.nullable(it)
    }
        ?: type
  }

  @Throws(CastException::class) fun castToJavaType(value: Any): Any

  fun getColumnConstraints(
      column: WebContifyCollectionColumnDto,
      tableName: String
  ): List<ConstraintEnforcementStep> {
    column.configuration?.let { configuration ->
      val constraints = mutableListOf<ConstraintEnforcementStep>()
      if (configuration.unique == true) {
        constraints.add(constraint("unique_${tableName}_${column.name}").unique(field(column.name)))
      }
      if (!configuration.inValues.isNullOrEmpty()) {
        constraints.add(
            constraint("in_values_${tableName}_${column.name}")
                .check(field(column.name).`in`(configuration.inValues?.map { it.value })))
      }
      return constraints.toList()
    }
    return listOf()
  }

  fun mapConfigurationToJSONB(configuration: WebContifyCollectionColumnConfigurationDto?): JSONB? {
    return JSONBtoJacksonConverter(WebContifyCollectionColumnConfigurationDto::class.java)
        .to(configuration)
  }

  fun mapJSONBToConfiguration(configuration: JSONB?): WebContifyCollectionColumnConfigurationDto? {
    return JSONBtoJacksonConverter(WebContifyCollectionColumnConfigurationDto::class.java)
        .from(configuration)
  }
}
