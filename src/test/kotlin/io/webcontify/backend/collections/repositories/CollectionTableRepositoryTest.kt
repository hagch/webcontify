package io.webcontify.backend.collections.repositories

import helpers.setups.repository.JooqTestSetup
import helpers.suppliers.collectionWithFields
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.collections.utils.camelToSnakeCase
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
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
  private final val firstField =
      WebContifyCollectionFieldDto(
          null, 1, "id", "id", WebcontifyCollectionFieldType.NUMBER, true, null)
  private final val secondField =
      WebContifyCollectionFieldDto(
          null, 1, "otherField", "otherField", WebcontifyCollectionFieldType.NUMBER, false, null)
  private final val firstFieldPrimary =
      WebContifyCollectionFieldDto(
          null, 1, "primary1", "primary1", WebcontifyCollectionFieldType.NUMBER, true, null)
  private final val secondFieldPrimary =
      WebContifyCollectionFieldDto(
          null, 1, "primary2", "primary2", WebcontifyCollectionFieldType.NUMBER, false, null)

  private final val onePrimaryKeyCollection =
      collectionWithFields(listOf(Pair("id", true), Pair("otherField", false)))
  private final val onePrimaryKeyFields =
      listOf(field(firstField.name.camelToSnakeCase()), field(secondField.name.camelToSnakeCase()))

  private final val compositePrimaryKeyCollection =
      collectionWithFields(listOf(Pair("primary1", true), Pair("primary2", true)))
  private final val compositePrimaryKeyFields =
      listOf(
          field(firstFieldPrimary.name.camelToSnakeCase()),
          field(secondFieldPrimary.name.camelToSnakeCase()))

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
  fun `(create) should throw exception if no field is primary key`() {
    val collection =
        WebContifyCollectionDto(
            1,
            "test",
            "Test",
            listOf(
                WebContifyCollectionFieldDto(
                    null, 1, "primary1", "id", WebcontifyCollectionFieldType.NUMBER, false, null)))

    assertThrows<UnprocessableContentException> { repository.create(collection) }
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(create) should throw exception if fields are empty`() {
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
