package io.webcontify.backend.collections.models.apis

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class WebContifyCollectionApiCreateRequest(
    @field:Pattern(regexp = "[a-z]+((\\d)|([A-Z0-9][a-z0-9]+))*([A-Z])?", message = "INVALID_NAME")
    @field:NotBlank(message = "NAME_REQUIRED")
    val name: String,
    val displayName: String = name,
    @field:Size(min = 1, message = "FIELD_REQUIRED")
    @field:Valid
    val fields: List<WebContifyCollectionFieldApiCreateRequest>
)
