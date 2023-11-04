package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Service

@Service
class BooleanColumnHandler : ColumnHandler {
  override fun getColumnType(): DataType<Boolean> {
    return SQLDataType.BOOLEAN
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.BOOLEAN
  }

  override fun castToJavaType(value: Any): Any {
    if (value is Boolean) {
      return value
    }
    if (value is String) {
      return value.toBoolean()
    }
    throw UnprocessableContentException()
  }
}
