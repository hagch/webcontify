package io.webcontify.backend.collections.repositories

import io.webcontify.backend.JooqTestSetup
import io.webcontify.backend.collections.models.dtos.WebContifyCollectionDto
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

  @Test
  @DisplayName("[1] getAll should return empty list if no collections are available")
  fun getAllShouldReturnEmptyList() {
    assertTrue(repository.getAll().isEmpty())
  }

  @Test
  @Sql("collection-with-columns.sql")
  @DisplayName("[2] getAll should return a collection with column definitions")
  fun getAllShouldReturnCollectionWithColumnDefinitions() {
    assertDoesNotThrow {
      repository.getAll().first { collection -> collection.columns?.isNotEmpty() ?: false }
    }
  }

  @Test
  @Sql("collection-without-columns.sql")
  @DisplayName("[3] getAll should return a collection without column definitions")
  fun getAllShouldReturnCollectionWithoutColumnDefinitions() {
    assertDoesNotThrow {
      repository.getAll().first { collection -> collection.columns?.isEmpty() ?: false }
    }
  }

  @Test
  @Sql("collection-with-columns.sql")
  @DisplayName("[4] getById should return a collection with column definitions")
  fun getByIdShouldReturnCollectionWithColumnDefinitions() {
    val collection = repository.getById(1)

    assertEquals(1, collection.id)
    assertTrue(collection.columns?.isNotEmpty() ?: false)
  }

  @Test
  @Sql("collection-without-columns.sql")
  @DisplayName("[5] getById should return a collection without column definitions")
  fun getByIdShouldReturnCollectionWithoutColumnDefinitions() {
    val collection = repository.getById(1)

    assertEquals(1, collection.id)
    assertTrue(collection.columns?.isEmpty() ?: false)
  }

  @Test
  @DisplayName("[6] getById should throw exception on collection does not exist")
  fun getByIdShouldThrowExceptionOnCollectionDoesNotExist() {
    assertThrows<RuntimeException> { repository.getById(2) }
  }

  @Test
  @Sql("collection-without-columns.sql")
  @DisplayName("[7] update should update name and displayName")
  fun updateByIdShouldUpdateNameAndDisplayName() {
    val collection = repository.getById(1)

    val newCollection =
        repository.update(collection.copy(displayName = "New DisplayName", name = "NEW_NAME"))

    assertNotEquals(collection.name, newCollection.name)
    assertEquals("NEW_NAME", newCollection.name)
    assertNotEquals(collection.displayName, newCollection.displayName)
    assertEquals("New DisplayName", newCollection.displayName)
    assertEquals(collection.id, newCollection.id)
  }

  @Test
  @DisplayName("[8] update should throw Exception on collection does not exist")
  fun updateByIdShouldThrowExceptionOnCollectionDoesNotExist() {
    assertThrows<RuntimeException> {
      repository.update(WebContifyCollectionDto(0, "", "", listOf()))
    }
  }

  @Test
  @Sql("collection-with-columns.sql")
  @DisplayName("[9] deleteById should be success full on resource exists")
  fun deleteByIdShouldDeleteExistingResource() {
    assertDoesNotThrow { repository.deleteById(1) }
  }

  @Test
  @DisplayName("[10] deleteById should not throw an exception on an resource that does not exist")
  fun deleteByIdShouldNotThrowAnExceptionOnResourceNotExist() {
    assertDoesNotThrow { repository.deleteById(2) }
  }

  @Test
  @DisplayName("[11] create should create collection")
  fun createShouldCreateCollection() {
    assertNotNull(repository.create(WebContifyCollectionDto(null, "TEST")))
    assertNotNull(repository.create(WebContifyCollectionDto(null, "TEST2")))
  }

  @Test
  @DisplayName("[12] create shouldThrow exception if name already exists")
  fun createShouldThrowExceptionIfNameAlreadyExists() {
    repository.create(WebContifyCollectionDto(null, "TEST"))
    assertThrows<RuntimeException> { repository.create(WebContifyCollectionDto(null, "TEST")) }
  }

  @Test
  @DisplayName("[13] create should ignore id and create a new entry")
  fun createShouldThrowExceptionIfIdAlreadyExists() {
    repository.create(WebContifyCollectionDto(1, "TEST"))
    repository.create(WebContifyCollectionDto(1, "TEST2"))
  }
}
