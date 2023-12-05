package io.webcontify.backend.collections.services.column.handler

import io.webcontify.backend.collections.models.dtos.CastException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnConfigurationDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter

interface ColumnHandler {
  fun getColumnType(): DataType<*>

  fun getColumnHandlerType(): WebcontifyCollectionColumnType

  @Throws(CastException::class) fun castToJavaType(value: Any): Any

  fun mapConfigurationToJSONB(configuration: WebContifyCollectionColumnConfigurationDto?): JSONB? {
    return JSONBtoJacksonConverter(WebContifyCollectionColumnConfigurationDto::class.java)
        .to(configuration)
  }

  fun mapJSONBToConfiguration(configuration: JSONB?): WebContifyCollectionColumnConfigurationDto? {
    return JSONBtoJacksonConverter(WebContifyCollectionColumnConfigurationDto::class.java)
        .from(configuration)
  }
}
