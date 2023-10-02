package io.webcontify.backend.collections.services.column.handler

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DataType

interface ColumnHandler {
  fun getColumnType(): DataType<*>

  fun getColumnHandlerType(): WebcontifyCollectionColumnType
}
