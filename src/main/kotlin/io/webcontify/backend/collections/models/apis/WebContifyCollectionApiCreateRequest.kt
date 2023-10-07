package io.webcontify.backend.collections.models.apis

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class WebContifyCollectionApiCreateRequest(
    @field:Pattern(
        regexp = "^(?!_)[0-9A-Z_]*(?<!_)$",
        message =
            "name cannot have leading or ending '_' and only those values are allowed 0-9, a-z, A-Z, _")
    val name: String,
    val displayName: String?,
    @field:Size(min = 1)
    @field:NotNull
    @field:Valid
    val columns: List<WebContifyCollectionColumnApiCreateRequest>
)

data class WebContifyCollectionColumnApiCreateRequest(
    @field:Pattern(
        regexp = "^(?!_)[0-9A-Z_]*(?<!_)$",
        message =
            "name cannot have leading or ending '_' and only those values are allowed 0-9, a-z, A-Z, _")
    val name: String,
    val displayName: String?,
    @field:NotNull(message = "type cannot be null") val type: WebcontifyCollectionColumnType,
    val isPrimaryKey: Boolean = false
)
