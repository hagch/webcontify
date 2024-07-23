package io.webcontify.backend.collections.models.apis

import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class WebContifyCollectionFieldApiUpdateRequest(
    @field:Pattern(regexp = "[a-z]+((\\d)|([A-Z0-9][a-z0-9]+))*([A-Z])?", message = "INVALID_NAME")
    val name: String,
    val displayName: String?,
    @field:NotNull(message = "TYPE_NON_NULLABLE") val type: WebcontifyCollectionFieldType,
    val isPrimaryKey: Boolean
)
