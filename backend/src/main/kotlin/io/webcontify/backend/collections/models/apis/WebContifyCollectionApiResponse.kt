package io.webcontify.backend.collections.models.apis

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto

data class WebContifyCollectionApiResponse(
    val id: Long,
    val name: String,
    val displayName: String = name,
    val fields: List<WebContifyCollectionFieldDto>
)
