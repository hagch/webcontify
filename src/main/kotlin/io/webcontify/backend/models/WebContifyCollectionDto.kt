package io.webcontify.backend.models

import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType

data class WebContifyCollectionDto(
    var id: Long,
    var name: String,
    var displayName: String,
    var columns: List<WebContifyCollectionColumnDto>?
)

data class WebContifyCollectionColumnDto(
    var name: String,
    var displayName: String,
    var type: WebcontifyCollectionColumnType
)
