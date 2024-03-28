package io.webcontify.backend.collections.models.apis

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class WebContifyCollectionApiCreateRequest(
    @field:Pattern(regexp = "^(?!_)[0-9a-z_]*(?<!_)$", message = "INVALID_NAME")
    @field:NotBlank(message = "NAME_REQUIRED")
    val name: String,
    val displayName: String = name,
    @field:Size(min = 1, message = "COLUMN_REQUIRED")
    @field:Valid
    val columns: List<WebContifyCollectionColumnApiCreateRequest>,
    val relations: Set<WebContifyCollectionRelationApiCreateRequest>?
)
