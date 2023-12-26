package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import java.util.*
import org.jooq.DataType
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Service

@Service
class UuidColumnHandler : ColumnHandler<UUID> {

  override fun getColumnType(): DataType<UUID> {
    return SQLDataType.UUID
  }

  override fun getColumnType(column: WebContifyCollectionColumnDto): DataType<UUID> {
    var type = super.getColumnType(column)
    if (column.isPrimaryKey) {
      type = type.defaultValue(DSL.uuid())
    }
    return type
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.UUID
  }

  override fun castToJavaType(value: Any?): UUID? {
    if (value is UUID) {
      return value
    }
    if (value is String) {
      return UUID.fromString(value)
    }
    throw CastException()
  }
}
