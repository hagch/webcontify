package io.webcontify.backend.collections.services.column.handler

import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DataType

interface ColumnHandler {
  fun getColumnType(): DataType<*>

  fun getColumnHandlerType(): WebcontifyCollectionColumnType

  @Throws(CastException::class) fun castToJavaType(value: Any): Any
}
