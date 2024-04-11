package io.webcontify.backend.collections.repositories

import helpers.setups.repository.JooqTestSetup
import helpers.suppliers.collectionWithColumns
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionColumnDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionColumnType
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
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
          null, 1, "id", "id", WebcontifyCollectionColumnType.NUMBER, true, null)
  private final val secondColumn =
      WebContifyCollectionColumnDto(
          null, 1, "otherColumn", "otherColumn", WebcontifyCollectionColumnType.NUMBER, false, null)
  private final val firstColumnPrimary =
      WebContifyCollectionColumnDto(
          null, 1, "primary1", "primary1", WebcontifyCollectionColumnType.NUMBER, true, null)
  private final val secondColumnPrimary =
      WebContifyCollectionColumnDto(
          null, 1, "primary2", "primary2", WebcontifyCollectionColumnType.NUMBER, false, null)

  private final val onePrimaryKeyCollection =
      collectionWithColumns(listOf(Pair("id", true), Pair("otherColumn", false)))
  private final val onePrimaryKeyFields = listOf(field(firstColumn.name), field(secondColumn.name))

  private final val compositePrimaryKeyCollection =
      collectionWithColumns(listOf(Pair("primary1", true), Pair("primary2", true)))
  private final val compositePrimaryKeyFields =
      listOf(field(firstColumnPrimary.name), field(secondColumnPrimary.name))

  @Test
  @Sql("/cleanup.sql")
  fun `(create) should create table with primary key`() {
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
  @Sql("/cleanup.sql")
  fun `(create) should create table with composite primary key`() {
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
  @Sql("/cleanup.sql")
  fun `(create) should throw exception if no column is primary key`() {
    val collection =
        WebContifyCollectionDto(
            1,
            "test",
            "Test",
            listOf(
                WebContifyCollectionColumnDto(
                    null, 1, "primary1", "id", WebcontifyCollectionColumnType.NUMBER, false, null)))

    assertThrows<UnprocessableContentException> { repository.create(collection) }
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(create) should throw exception if columns are empty`() {
    val collection = WebContifyCollectionDto(1, "test", "Test", listOf())

    assertThrows<UnprocessableContentException> { repository.create(collection) }
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(delete) should delete table`() {
    repository.delete("test")

    assertThrows<RuntimeException> { context.select().from("test").execute() }
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(delete) should not throw exception if table does not exist`() {
    assertDoesNotThrow { repository.delete("doesnotexist") }
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(updateName) should update name of table`() {
    repository.updateName("tester", "test")
    assertDoesNotThrow { context.select().from("tester").execute() }
    repository.updateName("test", "tester")
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(updateName) should not throw exception if table does not exist`() {
    assertDoesNotThrow { repository.updateName("tester", "test") }
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(updateName) should throw exception if new name is malformed or empty`() {
    assertThrows<UnprocessableContentException> { repository.updateName("", "test") }
    assertThrows<UnprocessableContentException> { repository.updateName("$", "test") }
  }
}
