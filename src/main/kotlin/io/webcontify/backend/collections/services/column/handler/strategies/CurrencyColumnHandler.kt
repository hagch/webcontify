package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import java.math.BigDecimal
import org.jooq.DataType
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Service

@Service
class CurrencyColumnHandler : ColumnHandler {
  override fun getColumnType(): DataType<BigDecimal> {
    return SQLDataType.DECIMAL
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.CURRENCY
  }

  override fun castToJavaType(value: Any): Any {
    throw UnprocessableContentException()
  }
}
