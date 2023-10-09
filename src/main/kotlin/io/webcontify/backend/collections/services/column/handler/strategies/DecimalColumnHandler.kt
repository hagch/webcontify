package io.webcontify.backend.collections.services.column.handler.strategies

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
}
