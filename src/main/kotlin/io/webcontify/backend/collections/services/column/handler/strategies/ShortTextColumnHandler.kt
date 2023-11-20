package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Service

@Service
class ShortTextColumnHandler : ColumnHandler {
  override fun getColumnType(): DataType<String> {
    return SQLDataType.VARCHAR
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.SHORT_TEXT
  }

  override fun castToJavaType(value: Any): Any {
    if (value is String) {
      return value
    }
    throw CastException()
  }
}
