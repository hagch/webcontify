package io.webcontify.backend.collections.models.apis

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.collections.services.field.handler.*
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class WebContifyCollectionFieldApiCreateRequest(
    @field:Pattern(regexp = "[a-z]+((\\d)|([A-Z0-9][a-z0-9]+))*([A-Z])?", message = "INVALID_NAME")
    @field:NotBlank(message = "NAME_REQUIRED")
    val name: String,
    val displayName: String = name,
    @field:NotNull(message = "TYPE_NON_NULLABLE") val type: WebcontifyCollectionFieldType,
    val isPrimaryKey: Boolean = false,
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldTextConfigurationDto::class, name = TEXT_FIELD_TYPE),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldNumberConfigurationDto::class,
            name = NUMBER_FIELD_TYPE),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldDecimalConfigurationDto::class,
            name = DECIMAL_FIELD_TYPE),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldTimestampConfigurationDto::class,
            name = TIMESTAMP_FIELD_TYPE),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldUuidConfigurationDto::class, name = UUID_FIELD_TYPE),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldBooleanConfigurationDto::class,
            name = BOOLEAN_FIELD_TYPE),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldRelationMirrorConfigurationDto::class,
            name = RELATION_MIRROR_FIELD_TYPE),
    )
    val configuration: WebContifyCollectionFieldConfigurationDto<Any>?
)
