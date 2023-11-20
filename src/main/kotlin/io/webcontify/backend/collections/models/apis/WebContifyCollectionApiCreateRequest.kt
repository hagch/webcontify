package io.webcontify.backend.collections.models.apis

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class WebContifyCollectionApiCreateRequest(
    @field:Pattern(regexp = "^(?!_)[0-9a-z_]*(?<!_)$", message = "INVALID_NAME") val name: String,
    val displayName: String?,
    @field:Size(min = 1, message = "COLUMN_REQUIRED")
    @field:Valid
    val columns: List<WebContifyCollectionColumnApiCreateRequest>
)

data class WebContifyCollectionColumnApiCreateRequest(
    @field:Pattern(regexp = "^(?!_)[0-9a-z_]*(?<!_)$", message = "INVALID_NAME") val name: String,
    val displayName: String?,
    @field:NotNull(message = "TYPE_NON_NULLABLE") val type: WebcontifyCollectionColumnType,
    val isPrimaryKey: Boolean = false
)
