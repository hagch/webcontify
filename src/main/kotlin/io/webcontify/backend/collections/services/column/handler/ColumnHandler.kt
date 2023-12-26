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

interface ColumnHandler<T> {
  fun getColumnType(): DataType<T>

  fun getColumnHandlerType(): WebcontifyCollectionColumnType

  fun getColumnType(column: WebContifyCollectionColumnDto): DataType<T> {
    var type = getColumnType()
    column.configuration?.let {
      if (column.isPrimaryKey) {
        type = type.nullable(false)
      } else {
        if (it.nullable != null) {
          type = type.nullable(it.nullable!!)
        }
        if (it.defaultValue != null) {
          type = type.defaultValue(castToJavaType(it.defaultValue!!))
        }
      }
    }
    return type
  }

  @Throws(CastException::class) fun castToJavaType(value: Any?): T?

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
                .check(field(column.name).`in`(configuration.inValues?.map { it })))
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
