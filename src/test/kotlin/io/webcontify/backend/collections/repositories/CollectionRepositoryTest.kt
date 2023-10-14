package io.webcontify.backend.collections.repositories

import helpers.setups.JooqTestSetup
import helpers.suppliers.collectionWithEmptyColumns
import helpers.suppliers.collectionWithNameCollection
import helpers.suppliers.collectionWithNameTest
import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql

class CollectionRepositoryTest(
    @Autowired val context: DSLContext,
    @Autowired val repository: CollectionRepository
) : JooqTestSetup() {

  val collectionId = 1

  @Test
  @DisplayName("getAll should return empty list if no collections are available")
  fun getAllShouldReturnEmptyList() {
    assertTrue(repository.getAll().isEmpty())
  }

  @Test
  @Sql("collection-with-columns.sql")
  @DisplayName("getAll should return a collection with column definitions")
  fun getAllShouldReturnCollectionWithColumnDefinitions() {
    assertDoesNotThrow {
      repository.getAll().first { collection -> !collection.columns.isNullOrEmpty() }
    }
  }

  @Test
  @Sql("collection-without-columns.sql")
  @DisplayName("getAll should return a collection without column definitions")
  fun getAllShouldReturnCollectionWithoutColumnDefinitions() {
    assertDoesNotThrow {
      repository.getAll().first { collection -> collection.columns.isNullOrEmpty() }
    }
  }

  @Test
  @Sql("collection-with-columns.sql")
  @DisplayName("getById should return a collection with column definitions")
  fun getByIdShouldReturnCollectionWithColumnDefinitions() {
    val collection = repository.getById(collectionId)

    assertEquals(collectionId, collection.id)
    assertTrue(!collection.columns.isNullOrEmpty())
  }

  @Test
  @Sql("collection-without-columns.sql")
  @DisplayName("getById should return a collection without column definitions")
  fun getByIdShouldReturnCollectionWithoutColumnDefinitions() {
    val collection = repository.getById(collectionId)

    assertEquals(collectionId, collection.id)
    assertTrue(collection.columns.isNullOrEmpty())
  }

  @Test
  @DisplayName("getById should throw exception on collection does not exist")
  fun getByIdShouldThrowExceptionOnCollectionDoesNotExist() {
    assertThrows<NotFoundException> { repository.getById(2) }
  }

  @Test
  @Sql("collection-without-columns.sql")
  @DisplayName("update should update name and displayName")
  fun updateShouldUpdateNameAndDisplayName() {
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
  @DisplayName("update should throw Exception on collection does not exist")
  fun updateShouldThrowExceptionOnCollectionDoesNotExist() {
    assertThrows<NotFoundException> { repository.update(collectionWithEmptyColumns()) }
  }

  @Test
  @Sql("collection-with-columns.sql")
  @DisplayName("deleteById should be success full on resource exists")
  fun deleteByIdShouldDeleteExistingResource() {
    assertDoesNotThrow { repository.deleteById(collectionId) }
  }

  @Test
  @DisplayName("deleteById should not throw an exception on an resource that does not exist")
  fun deleteByIdShouldNotThrowAnExceptionOnResourceNotExist() {
    assertDoesNotThrow { repository.deleteById(2) }
  }

  @Test
  @DisplayName("create should create collection")
  fun createShouldCreateCollection() {
    assertNotNull(repository.create(collectionWithNameTest()))
    assertNotNull(repository.create(collectionWithNameCollection()))
  }

  @Test
  @DisplayName("create shouldThrow exception if name already exists")
  fun createShouldThrowExceptionIfNameAlreadyExists() {
    repository.create(collectionWithNameTest())
    assertThrows<AlreadyExistsException> { repository.create(collectionWithNameTest()) }
  }

  @Test
  @DisplayName("create should ignore id and create a new entry")
  fun createShouldThrowExceptionIfIdAlreadyExists() {
    repository.create(collectionWithNameTest())
    repository.create(collectionWithNameCollection())
  }
}
