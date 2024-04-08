package io.webcontify.backend.collections.repositories

import helpers.setups.repository.JooqTestSetup
import helpers.suppliers.firstSqlInsertedField
import helpers.suppliers.secondSqlInsertedField
import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql

class CollectionFieldRepositoryTest(
    @Autowired val context: DSLContext,
    @Autowired val repository: CollectionFieldRepository
) : JooqTestSetup() {

  val firstSqlField = firstSqlInsertedField()

  @Test
  @Sql("/cleanup.sql", "collection-without-fields.sql")
  fun `(getAll) should return empty list if no fields for collection exists`() {
    assertTrue(repository.getAllForCollection(firstSqlField.collectionId).isEmpty())
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-fields.sql")
  fun `(getAllForCollection) should return list if fields for collection exists`() {
    assertTrue(repository.getAllForCollection(firstSqlField.collectionId).isNotEmpty())
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(getAllForCollection) should return empty list for non existent collection`() {
    assertTrue(repository.getAllForCollection(firstSqlField.collectionId).isEmpty())
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-fields.sql")
  fun `(getByCollectionIdAndName) should return field`() {
    val field = repository.getByCollectionIdAndName(firstSqlField.collectionId, firstSqlField.name)

    assertEquals(firstSqlField.collectionId, field.collectionId)
    assertEquals(firstSqlField.name, field.name)
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-fields.sql")
  fun `(getByCollectionIdAndName) should throw exception if field does not exist`() {
    assertThrows<NotFoundException> {
      repository.getByCollectionIdAndName(firstSqlField.collectionId, "Does not exist")
    }
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-fields.sql")
  fun `(update) should update field`() {
    val newDisplayName = "New DisplayName"
    val newName = "NEW_NAME"
    val field = repository.getByCollectionIdAndName(firstSqlField.collectionId, firstSqlField.name)

    val newCollection =
        repository.update(field.copy(displayName = newDisplayName, name = newName), field.name)

    assertNotEquals(field.name, newCollection.name)
    assertEquals(newName, newCollection.name)
    assertNotEquals(field.displayName, newCollection.displayName)
    assertEquals(newDisplayName, newCollection.displayName)
    assertEquals(field.collectionId, newCollection.collectionId)
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-fields.sql")
  fun `(update) should throw Exception on field does not exist`() {
    assertThrows<NotFoundException> {
      repository.update(firstSqlInsertedField().copy(collectionId = 2), "Test")
    }
    assertThrows<NotFoundException> { repository.update(firstSqlInsertedField(), "test") }
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-multiple-fields.sql")
  fun `(update) should throw Exception on another field with name exists`() {
    assertThrows<AlreadyExistsException> {
      repository.update(secondSqlInsertedField(), firstSqlInsertedField().name)
    }
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-fields.sql")
  fun `(deleteById) should be success full on resource exists`() {
    assertDoesNotThrow {
      repository.deleteById(firstSqlInsertedField().collectionId, firstSqlInsertedField().name)
    }
  }

  @Test
  fun `(deleteById) should not throw an exception on an resource that does not exist`() {
    assertDoesNotThrow { repository.deleteById(2, "Not Exists") }
  }

  @Test
  @Sql("/cleanup.sql", "collection-without-fields.sql")
  fun `(create) should create field`() {
    assertNotNull(repository.create(firstSqlInsertedField()))
    assertNotNull(repository.create(secondSqlInsertedField()))
  }

  @Test
  @Sql("/cleanup.sql", "collection-without-fields.sql")
  fun `(create) should throw exception if name already exists`() {
    repository.create(firstSqlInsertedField())
    assertThrows<AlreadyExistsException> { repository.create(firstSqlInsertedField()) }
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(create) should throw exception if collection does not exist`() {
    assertThrows<NotFoundException> { repository.create(firstSqlInsertedField()) }
  }
}
