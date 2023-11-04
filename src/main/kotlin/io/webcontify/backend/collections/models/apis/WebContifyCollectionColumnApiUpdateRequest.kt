package io.webcontify.backend.collections.models.apis

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import jakarta.validation.constraints.Pattern

data class WebContifyCollectionColumnApiUpdateRequest(
    @field:Pattern(
        regexp = "^(?!_)[0-9A-Z_]*(?<!_)$",
        message =
            "name cannot have leading or ending '_' and only those values are allowed 0-9, A-Z, _")
    val name: String,
    val displayName: String?,
    val type: WebcontifyCollectionColumnType,
    val isPrimaryKey: Boolean
)
