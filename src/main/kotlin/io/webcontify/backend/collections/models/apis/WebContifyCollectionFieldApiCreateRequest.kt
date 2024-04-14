package io.webcontify.backend.collections.models.apis

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class WebContifyCollectionFieldApiCreateRequest(
    @field:Pattern(regexp = "^(?!_)[0-9a-z_]*(?<!_)$", message = "INVALID_NAME")
    @field:NotBlank(message = "NAME_REQUIRED")
    val name: String,
    val displayName: String = name,
    @field:NotNull(message = "TYPE_NON_NULLABLE") val type: WebcontifyCollectionFieldType,
    val isPrimaryKey: Boolean = false,
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldTextConfigurationDto::class, name = "TEXT"),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldNumberConfigurationDto::class, name = "NUMBER"),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldDecimalConfigurationDto::class, name = "DECIMAL"),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldTimestampConfigurationDto::class, name = "TIMESTAMP"),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldUuidConfigurationDto::class, name = "UUID"),
        JsonSubTypes.Type(
            value = WebContifyCollectionFieldBooleanConfigurationDto::class, name = "BOOLEAN"),
    )
    val configuration: WebContifyCollectionFieldConfigurationDto<Any>?
)
