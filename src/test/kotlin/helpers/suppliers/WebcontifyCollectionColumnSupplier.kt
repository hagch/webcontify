package helpers.suppliers

import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType

fun firstSqlInsertedColumn(): WebContifyCollectionColumnDto {
  return WebContifyCollectionColumnDto(
      null, 1, "name", "DisplayName", WebcontifyCollectionColumnType.NUMBER, true, null)
}

fun secondSqlInsertedColumn(): WebContifyCollectionColumnDto {
  return WebContifyCollectionColumnDto(
      null, 1, "name2", "DisplayName", WebcontifyCollectionColumnType.NUMBER, true, null)
}
