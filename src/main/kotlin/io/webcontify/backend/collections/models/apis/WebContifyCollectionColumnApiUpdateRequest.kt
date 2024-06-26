package io.webcontify.backend.collections.models.apis

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class WebContifyCollectionColumnApiUpdateRequest(
    @field:Pattern(regexp = "^(?!_)[0-9a-z_]*(?<!_)$", message = "INVALID_NAME") val name: String,
    val displayName: String?,
    @field:NotNull(message = "TYPE_NON_NULLABLE") val type: WebcontifyCollectionColumnType,
    val isPrimaryKey: Boolean
)
