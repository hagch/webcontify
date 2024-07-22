package io.webcontify.backend.collections.repositories

import helpers.setups.repository.JooqTestSetup
import helpers.suppliers.collectionWithFields
import helpers.suppliers.relationMirrorField
import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionFieldDto
import io.webcontify.backend.jooq.enums.WebcontifyCollectionFieldType
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.test.context.jdbc.Sql

class CollectionTableFieldRepositoryTest(
    @Autowired val context: DSLContext,
    @Autowired val repository: CollectionTableFieldRepository
) : JooqTestSetup() {
  private final val firstField =
      WebContifyCollectionFieldDto(
          1, 1, "id", "id", WebcontifyCollectionFieldType.NUMBER, true, null)
  private final val secondField =
      WebContifyCollectionFieldDto(
          1, 1, "otherfield", "otherField", WebcontifyCollectionFieldType.NUMBER, false, null)
  private final val collection =
      collectionWithFields(listOf(Pair("id", true), Pair("otherfield", false)))
  private final val fields = listOf(DSL.field(firstField.name), DSL.field(secondField.name))

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(create) field should add field to table`() {
    val newField =
        WebContifyCollectionFieldDto(
            null,
            1,
            "otherField2",
            "otherField2",
            WebcontifyCollectionFieldType.NUMBER,
            false,
            null)
    repository.create(collection, newField)

    assertDoesNotThrow {
      context
          .insertInto(DSL.table(collection.name), fields.plus(DSL.field(newField.name)))
          .values(1, 1, 1)
          .execute()
    }
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(create) should not add relation mirror field on table`() {
    assertNotNull(repository.create(collection, relationMirrorField()))

    val exception =
        assertThrows<BadSqlGrammarException> {
          context
              .selectFrom(collection.name)
              .where(field(relationMirrorField().name).eq(0))
              .execute()
        }
    assertEquals(
        "jOOQ; bad SQL grammar [select * from test where relation_mirror = ?]", exception.message)
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(create) field should throw exception if field already exists`() {
    val newField =
        WebContifyCollectionFieldDto(
            null, 1, "otherfield", "otherField", WebcontifyCollectionFieldType.NUMBER, false, null)
    assertThrows<AlreadyExistsException> { repository.create(collection, newField) }
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(create) field should throw exception if field name is empty`() {
    val newField2 =
        WebContifyCollectionFieldDto(
            null, 1, "", "", WebcontifyCollectionFieldType.NUMBER, false, null)
    assertThrows<UnprocessableContentException> { repository.create(collection, newField2) }
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(delete) field should delete field from table`() {
    repository.delete(collection, secondField.name)
    assertDoesNotThrow {
      context.insertInto(DSL.table(collection.name), DSL.field(firstField.name)).values(1).execute()
    }
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(delete) field should not throw exception if field is used in constraint`() {
    assertDoesNotThrow { repository.delete(collection, firstField.name) }
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(delete) field should not throw exception if field not exists`() {
    assertDoesNotThrow { repository.delete(collection, "does_not_exist") }
  }

  @Test
  fun `(delete) field should not throw exception if table not exists`() {
    assertDoesNotThrow {
      repository.delete(collection.copy(name = "DOES_NOT_EXIST"), secondField.name)
    }
  }

  @Test
  fun `(update) field should throw exception if table not exists`() {
    assertThrows<UnprocessableContentException> {
      repository.update(collection, firstField.copy(name = "new"), 1)
    }
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(update) field should throw exception if old field not exists`() {
    assertThrows<NotFoundException> {
      repository.update(collection, firstField.copy(name = "new"), 33)
    }
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(update) field should throw exception if old field not exists on table`() {
    val collection =
        collection.copy(fields = listOf(firstField, secondField, firstField.copy(name = "new")))
    assertThrows<UnprocessableContentException> {
      repository.update(collection, firstField.copy(name = "newer"), firstField.id!!)
    }
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(update) field should update field`() {
    repository.update(collection, secondField.copy(name = "test"), 2)

    assertDoesNotThrow {
      context
          .insertInto(DSL.table(collection.name), DSL.field("test"), DSL.field("id"))
          .values(1, 1)
          .execute()
    }
  }

  @Test
  fun `(update) field should throw exception if field type has changed`() {
    assertThrows<UnprocessableContentException> {
      repository.update(
          collection,
          firstField.copy(name = "new", type = WebcontifyCollectionFieldType.TIMESTAMP),
          1)
    }
  }

  @Test
  @Sql("/cleanup.sql", "create-test-table.sql")
  fun `(update) field should throw exception if new name is empty`() {
    assertThrows<UnprocessableContentException> {
      repository.update(collection, firstField.copy(name = ""), 1)
    }
  }
}
