package io.webcontify.backend.collections.services.field.handler.strategies

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldRelationMirrorConfigurationDto
import io.webcontify.backend.collections.services.field.handler.FieldHandler
import io.webcontify.backend.collections.services.field.handler.RELATION_MIRROR_FIELD_TYPE
import org.jooq.ConstraintEnforcementStep
import org.jooq.DataType
import org.jooq.JSONB
import org.jooq.jackson.extensions.converters.JSONBtoJacksonConverter
import org.springframework.stereotype.Component

@Component(RELATION_MIRROR_FIELD_TYPE)
class RelationMirrorFieldHandler : FieldHandler<Any> {

  private val converter =
      JSONBtoJacksonConverter(WebContifyCollectionFieldRelationMirrorConfigurationDto::class.java)

  override fun getFieldType(): DataType<Any>? {
    return null
  }

  override fun getFieldConstraints(
      field: WebContifyCollectionFieldDto,
      tableName: String
  ): List<ConstraintEnforcementStep> {
    return emptyList()
  }

  override fun castToJavaType(value: Any?): Any? {
    return null
  }

  override fun mapJSONBToConfiguration(
      configuration: JSONB?
  ): WebContifyCollectionFieldRelationMirrorConfigurationDto? {
    return converter.from(configuration)
  }
}
