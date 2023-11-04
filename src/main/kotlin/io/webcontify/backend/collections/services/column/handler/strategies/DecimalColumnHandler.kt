package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import java.math.BigDecimal
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Service

@Service
class DecimalColumnHandler : ColumnHandler {
  override fun getColumnType(): DataType<BigDecimal> {
    return SQLDataType.DECIMAL
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.DECIMAL
  }

  override fun castToJavaType(value: Any): Any {
    if (value is Double) {
      return value
    }
    if (value is String) {
      return value.toDouble()
    }
    throw UnprocessableContentException()
  }
}
