package io.webcontify.backend.collections.models.apis

import jakarta.validation.constraints.Pattern

data class WebContifyCollectionApiUpdateRequest(
    @field:Pattern(
        regexp = "^(?!_)[0-9A-Z_]*(?<!_)$",
        message =
            "name cannot have leading or ending '_' and only those values are allowed 0-9, a-z, A-Z, _")
    val name: String,
    val displayName: String?
)