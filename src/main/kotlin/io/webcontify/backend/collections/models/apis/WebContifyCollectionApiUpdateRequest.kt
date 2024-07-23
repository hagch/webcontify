package io.webcontify.backend.collections.models.apis

import jakarta.validation.constraints.Pattern

data class WebContifyCollectionApiUpdateRequest(
    @field:Pattern(regexp = "[a-z]+((\\d)|([A-Z0-9][a-z0-9]+))*([A-Z])?", message = "INVALID_NAME")
    val name: String,
    val displayName: String?
)
