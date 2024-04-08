package helpers.suppliers

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType

fun firstSqlInsertedField(): WebContifyCollectionFieldDto {
  return WebContifyCollectionFieldDto(
      1, 1, "name", "DisplayName", WebcontifyCollectionFieldType.NUMBER, true, null)
}

fun secondSqlInsertedField(): WebContifyCollectionFieldDto {
  return WebContifyCollectionFieldDto(
      2, 1, "name2", "DisplayName", WebcontifyCollectionFieldType.NUMBER, true, null)
}

fun relationMirrorField(): WebContifyCollectionFieldDto {
  return WebContifyCollectionFieldDto(
      2,
      1,
      "relation_mirror",
      "DisplayName",
      WebcontifyCollectionFieldType.RELATION_MIRROR,
      true,
      null)
}
