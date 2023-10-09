package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Service

@Service
class TimestampColumnHandler : ColumnHandler {
  override fun getColumnType(): DataType<*> {
    return SQLDataType.TIMESTAMP
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.TIMESTAMP
  }
}
