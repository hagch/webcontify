package helpers.suppliers

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType

fun collectionWithNameCollection(): WebContifyCollectionDto {
  return WebContifyCollectionDto(null, "COLLECTION")
}

fun collectionWithNameTest(): WebContifyCollectionDto {
  return WebContifyCollectionDto(null, "TEST")
}

fun collectionWithEmptyColumns(): WebContifyCollectionDto {
  return WebContifyCollectionDto(0, "", "", listOf())
}

fun collectionWithColumns(fieldIsPrimaryMap: List<Pair<String, Boolean>>): WebContifyCollectionDto {
  return WebContifyCollectionDto(
      1,
      "test",
      "Test",
      fieldIsPrimaryMap.map {
        WebContifyCollectionColumnDto(
            1, it.first, it.first, WebcontifyCollectionColumnType.NUMBER, it.second)
      })
}
