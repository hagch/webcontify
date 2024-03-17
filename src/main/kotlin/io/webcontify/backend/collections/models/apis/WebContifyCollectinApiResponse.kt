package io.webcontify.backend.collections.models.apis

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto

data class WebContifyCollectionApiResponse(
    val id: Int,
    val name: String,
    val displayName: String = name,
    val columns: List<WebContifyCollectionColumnDto>,
    val relations: List<WebContifyCollectionRelationApiResponse> = listOf()
)
