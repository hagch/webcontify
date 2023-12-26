package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class TextColumnHandler : ColumnHandler<String> {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionColumnTextConfigurationDto::class.java)

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionColumnConfigurationDto? {
    return converter.from(configuration)
  }

  override fun getColumnType(): DataType<String> {
    return SQLDataType.VARCHAR
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.TEXT
  }

  override fun getColumnConstraints(
      column: WebContifyCollectionColumnDto,
      tableName: String
  ): List<ConstraintEnforcementStep> {
    val list = super.getColumnConstraints(column, tableName).toMutableList()
    column.configuration?.let {
      if (it is WebContifyCollectionColumnTextConfigurationDto) {
        val field = DSL.field(column.name, SQLDataType.VARCHAR)
        if (it.minLength != null) {
          list.add(
              DSL.constraint("min_length_${tableName}_${column.name}")
                  .check(DSL.length(field).greaterOrEqual(it.minLength)))
        }
        if (it.maxLength != null) {
          list.add(
              DSL.constraint("max_length_${tableName}_${column.name}")
                  .check(DSL.length(field).lessOrEqual(it.maxLength)))
        }
        if (it.regex != null) {
          list.add(
              DSL.constraint("regex_${tableName}_${column.name}").check(field.likeRegex(it.regex)))
        }
      }
    }
    return list.toList()
  }

  override fun castToJavaType(value: Any?): String? {
    if (value is String) {
      return value
    }
    throw CastException()
  }
}
