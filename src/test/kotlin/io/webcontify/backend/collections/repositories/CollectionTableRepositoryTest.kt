package io.webcontify.backend.collections.repositories

import helpers.setups.JooqTestSetup
import helpers.suppliers.collectionWithColumns
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql

class CollectionTableRepositoryTest(
    @Autowired val context: DSLContext,
    @Autowired val repository: CollectionTableRepository
) : JooqTestSetup() {
  private final val firstColumn =
      WebContifyCollectionColumnDto(
          1, "id", "id", WebcontifyCollectionColumnType.NUMBER, true, null)
  private final val secondColumn =
      WebContifyCollectionColumnDto(
          1, "otherColumn", "otherColumn", WebcontifyCollectionColumnType.NUMBER, false, null)
  private final val firstColumnPrimary =
      WebContifyCollectionColumnDto(
          1, "primary1", "primary1", WebcontifyCollectionColumnType.NUMBER, true, null)
  private final val secondColumnPrimary =
      WebContifyCollectionColumnDto(
          1, "primary2", "primary2", WebcontifyCollectionColumnType.NUMBER, false, null)

  private final val onePrimaryKeyCollection =
      collectionWithColumns(listOf(Pair("id", true), Pair("otherColumn", false)))
  private final val onePrimaryKeyFields = listOf(field(firstColumn.name), field(secondColumn.name))

  private final val compositePrimaryKeyCollection =
      collectionWithColumns(listOf(Pair("primary1", true), Pair("primary2", true)))
  private final val compositePrimaryKeyFields =
      listOf(field(firstColumnPrimary.name), field(secondColumnPrimary.name))

  @Test
  @DisplayName("create should create table")
  fun createShouldCreateTableWithPrimaryKey() {
    repository.create(onePrimaryKeyCollection)

    assertDoesNotThrow { context.select().from(onePrimaryKeyCollection.name).execute() }
    assertDoesNotThrow {
      context
          .insertInto(table(onePrimaryKeyCollection.name), onePrimaryKeyFields)
          .values(1, 1)
          .execute()
    }
    assertThrows<RuntimeException> {
      context
          .insertInto(table(onePrimaryKeyCollection.name), onePrimaryKeyFields)
          .values(1, null)
          .execute()
    }
  }

  @Test
  @DisplayName("create should create table with composite primary key")
  fun createShouldCreateTableWithCompositePrimaryKey() {
    repository.create(compositePrimaryKeyCollection)

    assertDoesNotThrow { context.select().from(compositePrimaryKeyCollection.name).execute() }
    assertDoesNotThrow {
      context
          .insertInto(table(compositePrimaryKeyCollection.name), compositePrimaryKeyFields)
          .values(1, 1)
          .execute()
    }
    assertThrows<RuntimeException> {
      context
          .insertInto(table(compositePrimaryKeyCollection.name), compositePrimaryKeyFields)
          .values(1, 1)
          .execute()
    }
  }

  @Test
  @DisplayName("create should throw exception if no column is primary key")
  fun createShouldThrowExceptionIfNoColumnIsPrimaryKey() {
    val collection =
        WebContifyCollectionDto(
            1,
            "test",
            "Test",
            listOf(
                WebContifyCollectionColumnDto(
                    1, "primary1", "id", WebcontifyCollectionColumnType.NUMBER, false, null)))

    assertThrows<UnprocessableContentException> { repository.create(collection) }
  }

  @Test
  @DisplayName("create should throw exception if columns are empty")
  fun createShouldThrowExceptionIfColumnsAreEmpty() {
    val collection = WebContifyCollectionDto(1, "test", "Test", listOf())

    assertThrows<UnprocessableContentException> { repository.create(collection) }
  }

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("delete should delete table")
  fun deleteShouldDeleteTable() {
    repository.delete("test")

    assertThrows<RuntimeException> { context.select().from("test").execute() }
  }

  @Test
  @DisplayName("delete should not throw exception if table does not exist")
  fun deleteShouldNotThrowExceptionIfTableDoesNotExist() {
    assertDoesNotThrow { repository.delete("doesnotexist") }
  }

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("updateName should update name of table")
  fun updateNameShouldUpdateNameOfTable() {
    repository.updateName("tester", "test")
    assertDoesNotThrow { context.select().from("tester").execute() }
    repository.updateName("test", "tester")
  }

  @Test
  @DisplayName("updateName should not throw exception if table does not exist")
  fun updateNameShouldNotThrowExceptionIfTableDoesNotExist() {
    assertDoesNotThrow { repository.updateName("tester", "test") }
  }

  @Test
  @Sql("create-test-table.sql")
  @DisplayName("updateName should throw exception if new name is malformed or empty")
  fun updateNameShouldThrowExceptionIfNewNameIsMalformed() {
    assertThrows<UnprocessableContentException> { repository.updateName("", "test") }
    assertThrows<UnprocessableContentException> { repository.updateName("$", "test") }
  }
}
