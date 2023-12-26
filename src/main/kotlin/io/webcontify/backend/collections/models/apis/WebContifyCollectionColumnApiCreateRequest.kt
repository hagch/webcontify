package io.webcontify.backend.collections.models.apis

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.webcontify.backend.collections.models.dtos.*
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class WebContifyCollectionColumnApiCreateRequest(
    @field:Pattern(regexp = "^(?!_)[0-9a-z_]*(?<!_)$", message = "INVALID_NAME") val name: String,
    val displayName: String?,
    @field:NotNull(message = "TYPE_NON_NULLABLE") val type: WebcontifyCollectionColumnType,
    val isPrimaryKey: Boolean = false,
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(
            value = WebContifyCollectionColumnTextConfigurationDto::class, name = "TEXT"),
        JsonSubTypes.Type(
            value = WebContifyCollectionColumnNumberConfigurationDto::class, name = "NUMBER"),
        JsonSubTypes.Type(
            value = WebContifyCollectionColumnDecimalConfigurationDto::class, name = "DECIMAL"),
        JsonSubTypes.Type(
            value = WebContifyCollectionColumnTimestampConfigurationDto::class, name = "TIMESTAMP"),
        JsonSubTypes.Type(
            value = WebContifyCollectionColumnUuidConfigurationDto::class, name = "UUID"),
        JsonSubTypes.Type(
            value = WebContifyCollectionColumnBooleanConfigurationDto::class, name = "BOOLEAN"),
    )
    val configuration: WebContifyCollectionColumnConfigurationDto<Any>?
)
