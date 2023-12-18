package io.webcontify.backend.collections.repositories

import helpers.setups.JooqTestSetup
import helpers.suppliers.collectionWithColumns
import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql

class CollectionTableColumnRepositoryTest(
    @Autowired val context: DSLContext,
    @Autowired val repository: CollectionTableColumnRepository
) : JooqTestSetup() {
  private final val firstColumn =
      WebContifyCollectionColumnDto(
          1, "id", "id", WebcontifyCollectionColumnType.NUMBER, true, null)
  private final val secondColumn =
      WebContifyCollectionColumnDto(
          1, "otherColumn", "otherColumn", WebcontifyCollectionColumnType.NUMBER, false, null)
  private final val collection =
      collectionWithColumns(listOf(Pair("id", true), Pair("otherColumn", false)))
  private final val fields = listOf(DSL.field(firstColumn.name), DSL.field(secondColumn.name))

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("Create column should add column to table")
  fun createColumnShouldAddColumnToTable() {
    val newColumn =
        WebContifyCollectionColumnDto(
            1, "otherColumn2", "otherColumn2", WebcontifyCollectionColumnType.NUMBER, false, null)
    repository.create(collection, newColumn)

    assertDoesNotThrow {
      context
          .insertInto(DSL.table(collection.name), fields.plus(DSL.field(newColumn.name)))
          .values(1, 1, 1)
          .execute()
    }
  }

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("Create column should throw exception if column already exists")
  fun createColumnShouldThrowExceptionIfColumnAlreadyExists() {
    val newColumn =
        WebContifyCollectionColumnDto(
            1, "otherColumn", "otherColumn", WebcontifyCollectionColumnType.NUMBER, false, null)
    assertThrows<AlreadyExistsException> { repository.create(collection, newColumn) }
  }

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("Create column should throw exception if column name is empty")
  fun createColumnShouldThrowExceptionIfColumnNameIsMalformed() {
    val newColumn2 =
        WebContifyCollectionColumnDto(1, "", "", WebcontifyCollectionColumnType.NUMBER, false, null)
    assertThrows<UnprocessableContentException> { repository.create(collection, newColumn2) }
  }

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("Delete column should delete column from table")
  fun deleteColumnShouldDeleteColumn() {
    repository.delete(collection, secondColumn.name)
    assertDoesNotThrow {
      context
          .insertInto(DSL.table(collection.name), DSL.field(firstColumn.name))
          .values(1)
          .execute()
    }
  }

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("Delete column should not throw exception if column is used in constraint")
  fun deleteColumnShouldNotThrowExceptionIfUsedInConstraint() {
    assertDoesNotThrow { repository.delete(collection, firstColumn.name) }
  }

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("Delete column should not throw exception if column not exists")
  fun deleteColumnShouldNotThrowExceptionIfColumnNotExists() {
    assertDoesNotThrow { repository.delete(collection, "does_not_exist") }
  }

  @Test
  @DisplayName("Delete column should not throw exception if table not exists")
  fun deleteColumnShouldNotThrowExceptionIfTableNotExists() {
    assertDoesNotThrow {
      repository.delete(collection.copy(name = "DOES_NOT_EXIST"), secondColumn.name)
    }
  }

  @Test
  @DisplayName("Update column should throw exception if table not exists")
  fun updateColumnShouldThrowExceptionIfTableNotExists() {
    assertThrows<UnprocessableContentException> {
      repository.update(collection, firstColumn.copy(name = "new"), "id")
    }
  }

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("Update column should throw exception if old column not exists")
  fun updateColumnShouldThrowExceptionIfColumnNotExists() {
    assertThrows<NotFoundException> {
      repository.update(collection, firstColumn.copy(name = "new"), "notExists")
    }
  }

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("Update column should throw exception if old column not exists on table")
  fun updateColumnShouldThrowExceptionIfColumnNotExistsOnTable() {
    val collection =
        collection.copy(columns = listOf(firstColumn, secondColumn, firstColumn.copy(name = "new")))
    assertThrows<UnprocessableContentException> {
      repository.update(collection, firstColumn.copy(name = "newer"), "new")
    }
  }

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("Update column should update column")
  fun updateColumnShouldUpdateColumn() {
    repository.update(collection, firstColumn.copy(name = "new"), "id")

    assertDoesNotThrow {
      context.insertInto(DSL.table(collection.name), DSL.field("new")).values(1).execute()
    }
  }

  @Test
  @DisplayName("Update column should throw exception if column type has changed")
  fun updateColumnShouldThrowExceptionIfColumnTypeHasChanged() {
    assertThrows<UnprocessableContentException> {
      repository.update(
          collection,
          firstColumn.copy(name = "new", type = WebcontifyCollectionColumnType.TIMESTAMP),
          "id")
    }
  }

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("Update column should throw exception if new name is empty")
  fun updateColumnShouldThrowExceptionIfColumnNameIsEmpty() {
    assertThrows<UnprocessableContentException> {
      repository.update(collection, firstColumn.copy(name = ""), "id")
    }
  }
}
