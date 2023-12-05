package io.webcontify.backend.collections.services.column.handler.strategies

import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnConfigurationDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnCurrencyConfigurationDto
import io.webcontify.backend.collections.services.column.handler.ColumnHandler
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import java.math.BigDecimal
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.impl.SQLDataType
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Service

@Service
class CurrencyColumnHandler : ColumnHandler {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionColumnCurrencyConfigurationDto::class.java)

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionColumnConfigurationDto? {
    return converter.from(configuration)
  }

  override fun getColumnType(): DataType<BigDecimal> {
    return SQLDataType.DECIMAL
  }

  override fun getColumnHandlerType(): WebcontifyCollectionColumnType {
    return WebcontifyCollectionColumnType.CURRENCY
  }

  override fun castToJavaType(value: Any): Any {
    throw CastException()
  }
}
