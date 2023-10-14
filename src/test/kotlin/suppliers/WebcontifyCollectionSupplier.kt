package suppliers

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto

fun collectionWithNameCollection(): WebContifyCollectionDto {
  return WebContifyCollectionDto(null, "COLLECTION")
}

fun collectionWithNameTest(): WebContifyCollectionDto {
  return WebContifyCollectionDto(null, "TEST")
}

fun collectionWithEmptyColumns(): WebContifyCollectionDto {
  return WebContifyCollectionDto(0, "", "", listOf())
}
