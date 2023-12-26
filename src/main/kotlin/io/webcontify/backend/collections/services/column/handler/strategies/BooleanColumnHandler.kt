package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Service

@Service
class BooleanColumnHandler : ColumnHandler<Boolean> {

  override fun getColumnType(): DataType<Boolean> {
    return SQLDataType.BOOLEAN
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.BOOLEAN
  }

  override fun castToJavaType(value: Any?): Boolean? {
    if (value is Boolean) {
      return value
    }
    if (value is String) {
      return value.toBoolean()
    }
    throw CastException()
  }
}
