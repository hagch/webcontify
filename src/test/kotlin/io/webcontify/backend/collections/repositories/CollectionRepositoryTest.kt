package io.webcontify.backend.collections.repositories

import helpers.setups.repository.JooqTestSetup
import helpers.suppliers.collectionWithEmptyFields
import helpers.suppliers.collectionWithNameCollection
import helpers.suppliers.collectionWithNameTest
import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql

class CollectionRepositoryTest(
    @Autowired val context: DSLContext,
    @Autowired val repository: CollectionRepository
) : JooqTestSetup() {

  val collectionId = 1L

  @Test
  @Sql("/cleanup.sql")
  fun `(getAll) should return empty list if no collections are available`() {
    assertTrue(repository.getAll().isEmpty())
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-fields.sql")
  fun `(getAll) should return a collection with field definitions`() {
    assertDoesNotThrow {
      repository.getAll().first { collection -> !collection.fields.isNullOrEmpty() }
    }
  }

  @Test
  @Sql("/cleanup.sql", "collection-without-fields.sql")
  fun `(getAll) should return a collection without field definitions`() {
    assertDoesNotThrow {
      repository.getAll().first { collection -> collection.fields.isNullOrEmpty() }
    }
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-fields.sql")
  fun `(getById) should return a collection with field definitions`() {
    val collection = repository.getById(collectionId)

    assertEquals(collectionId, collection.id)
    assertTrue(!collection.fields.isNullOrEmpty())
  }

  @Test
  @Sql("/cleanup.sql", "collection-without-fields.sql")
  fun `(getById) should return a collection without field definitions`() {
    val collection = repository.getById(collectionId)

    assertEquals(collectionId, collection.id)
    assertTrue(collection.fields.isNullOrEmpty())
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(getById) should throw exception on collection does not exist`() {
    assertThrows<NotFoundException> { repository.getById(2) }
  }

  @Test
  @Sql("/cleanup.sql", "collection-without-fields.sql")
  fun `(update) should update name and displayName`() {
    val newDisplayName = "New DisplayName"
    val newName = "NEW_NAME"
    val collection = repository.getById(collectionId)

    val newCollection =
        repository.update(collection.copy(displayName = newDisplayName, name = newName))

    assertNotEquals(collection.name, newCollection.name)
    assertEquals(newName, newCollection.name)
    assertNotEquals(collection.displayName, newCollection.displayName)
    assertEquals(newDisplayName, newCollection.displayName)
    assertEquals(collection.id, newCollection.id)
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(update) should throw Exception on collection does not exist`() {
    assertThrows<NotFoundException> { repository.update(collectionWithEmptyFields()) }
  }

  @Test
  @Sql("/cleanup.sql", "collection-without-fields.sql")
  fun `(deleteById) should be success full on resource exists`() {
    assertDoesNotThrow { repository.deleteById(collectionId) }
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-fields.sql")
  fun `(deleteById) should not throw exception on collection with fields exist`() {
    assertDoesNotThrow { repository.deleteById(collectionId) }
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(deleteById) should not throw an exception on an resource that does not exist`() {
    assertDoesNotThrow { repository.deleteById(2) }
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(create) should create collection`() {
    assertNotNull(repository.create(collectionWithNameTest()))
    assertNotNull(repository.create(collectionWithNameCollection()))
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(create) shouldThrow exception if name already exists`() {
    repository.create(collectionWithNameTest())
    assertThrows<AlreadyExistsException> { repository.create(collectionWithNameTest()) }
  }

  @Test
  @Sql("/cleanup.sql")
  fun `(create) should ignore id and create a new entry`() {
    repository.create(collectionWithNameTest())
    repository.create(collectionWithNameCollection())
  }
}
