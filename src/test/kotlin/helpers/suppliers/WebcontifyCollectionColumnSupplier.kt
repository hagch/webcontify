package helpers.suppliers

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType

fun firstSqlInsertedColumn(): WebContifyCollectionColumnDto {
  return WebContifyCollectionColumnDto(
      1, "Name", "DisplayName", WebcontifyCollectionColumnType.NUMBER, true)
}

fun secondSqlInsertedColumn(): WebContifyCollectionColumnDto {
  return WebContifyCollectionColumnDto(
      1, "Name2", "DisplayName", WebcontifyCollectionColumnType.NUMBER, true)
}
