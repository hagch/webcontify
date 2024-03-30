package io.webcontify.backend.collections.repositories

import helpers.setups.repository.JooqTestSetup
import helpers.suppliers.firstSqlInsertedColumn
import helpers.suppliers.secondSqlInsertedColumn
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

class CollectionColumnRepositoryTest(
    @Autowired val context: DSLContext,
    @Autowired val repository: CollectionColumnRepository
) : JooqTestSetup() {

  val firstSqlColumn = firstSqlInsertedColumn()

  @Test
  @Sql("cleanup.sql", "collection-without-columns.sql")
  @DisplayName("getAll should return empty list if no columns for collection exists")
  fun getAllForCollectionShouldReturnEmptyListOnCollectionExist() {
    assertTrue(repository.getAllForCollection(firstSqlColumn.collectionId).isEmpty())
  }

  @Test
  @Sql("cleanup.sql", "collection-with-columns.sql")
  @DisplayName("getAllForCollection should return list if columns for collection exists")
  fun getAllForCollectionShouldList() {
    assertTrue(repository.getAllForCollection(firstSqlColumn.collectionId).isNotEmpty())
  }

  @Test
  @DisplayName("getAllForCollection should return empty list for non existent collection")
  fun getAllForCollectionShouldReturnEmptyListOnCollectionDoesNotExist() {
    assertTrue(repository.getAllForCollection(firstSqlColumn.collectionId).isEmpty())
  }

  @Test
  @Sql("cleanup.sql", "collection-with-columns.sql")
  @DisplayName("getById should return column")
  fun getByIdShouldReturnColumn() {
    val column = repository.getById(firstSqlColumn.collectionId, firstSqlColumn.name)

    assertEquals(firstSqlColumn.collectionId, column.collectionId)
    assertEquals(firstSqlColumn.name, column.name)
  }

  @Test
  @Sql("cleanup.sql", "collection-with-columns.sql")
  @DisplayName("getById should throw exception if column does not exist")
  fun getByIdShouldThrowException() {
    assertThrows<NotFoundException> {
      repository.getById(firstSqlColumn.collectionId, "Does not exist")
    }
  }

  @Test
  @Sql("cleanup.sql", "collection-with-columns.sql")
  @DisplayName("update should update column")
  fun updateShouldUpdateColumn() {
    val newDisplayName = "New DisplayName"
    val newName = "NEW_NAME"
    val column = repository.getById(firstSqlColumn.collectionId, firstSqlColumn.name)

    val newCollection =
        repository.update(column.copy(displayName = newDisplayName, name = newName), column.name)

    assertNotEquals(column.name, newCollection.name)
    assertEquals(newName, newCollection.name)
    assertNotEquals(column.displayName, newCollection.displayName)
    assertEquals(newDisplayName, newCollection.displayName)
    assertEquals(column.collectionId, newCollection.collectionId)
  }

  @Test
  @Sql("cleanup.sql", "collection-with-columns.sql")
  @DisplayName("update should throw Exception on column does not exist")
  fun updateShouldThrowExceptionOnColumnDoesNotExist() {
    assertThrows<NotFoundException> {
      repository.update(firstSqlInsertedColumn().copy(collectionId = 2), "Test")
    }
    assertThrows<NotFoundException> { repository.update(firstSqlInsertedColumn(), "test") }
  }

  @Test
  @Sql("cleanup.sql", "collection-with-multiple-columns.sql")
  @DisplayName("update should throw Exception on another column with name exists")
  fun updateShouldThrowExceptionOnAnotherColumnForCollectionAlreadyExists() {
    assertThrows<AlreadyExistsException> {
      repository.update(secondSqlInsertedColumn(), firstSqlInsertedColumn().name)
    }
  }

  @Test
  @Sql("cleanup.sql", "collection-with-columns.sql")
  @DisplayName("deleteById should be success full on resource exists")
  fun deleteByIdShouldDeleteExistingResource() {
    assertDoesNotThrow {
      repository.deleteById(firstSqlInsertedColumn().collectionId, firstSqlInsertedColumn().name)
    }
  }

  @Test
  @DisplayName("deleteById should not throw an exception on an resource that does not exist")
  fun deleteByIdShouldNotThrowAnExceptionOnResourceNotExist() {
    assertDoesNotThrow { repository.deleteById(2, "Not Exists") }
  }

  @Test
  @Sql("cleanup.sql", "collection-without-columns.sql")
  @DisplayName("create should create collection")
  fun createShouldCreateCollection() {
    assertNotNull(repository.create(firstSqlInsertedColumn()))
    assertNotNull(repository.create(secondSqlInsertedColumn()))
  }

  @Test
  @Sql("cleanup.sql", "collection-without-columns.sql")
  @DisplayName("create shouldThrow exception if name already exists")
  fun createShouldThrowExceptionIfNameAlreadyExists() {
    repository.create(firstSqlInsertedColumn())
    assertThrows<AlreadyExistsException> { repository.create(firstSqlInsertedColumn()) }
  }

  @Test
  @DisplayName("create should throw exception if collection does not exist")
  fun createShouldThrowExceptionIfCollectionDoesNotExist() {
    assertThrows<NotFoundException> { repository.create(firstSqlInsertedColumn()) }
  }
}
