package io.webcontify.backend.collections.models.apis

import jakarta.validation.constraints.Pattern

data class WebContifyCollectionApiUpdateRequest(
    @field:Pattern(regexp = "^(?!_)[0-9a-z_]*(?<!_)$", message = "INVALID_NAME") val name: String,
    val displayName: String?
)
