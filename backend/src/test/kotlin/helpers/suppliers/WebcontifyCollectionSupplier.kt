package helpers.suppliers

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType

fun collectionWithNameCollection(): WebContifyCollectionDto {
  return WebContifyCollectionDto(null, "collection")
}

fun collectionWithNameTest(): WebContifyCollectionDto {
  return WebContifyCollectionDto(null, "test")
}

fun collectionWithEmptyFields(): WebContifyCollectionDto {
  return WebContifyCollectionDto(0, "", "", listOf())
}

fun collectionWithFields(fieldIsPrimaryMap: List<Pair<String, Boolean>>): WebContifyCollectionDto {
  return WebContifyCollectionDto(
      1,
      "test",
      "Test",
      fieldIsPrimaryMap.mapIndexed() { index, it ->
        WebContifyCollectionFieldDto(
            index + 1L,
            1,
            it.first,
            it.first,
            WebcontifyCollectionFieldType.NUMBER,
            it.second,
            null)
      })
}
