package io.webcontify.backend.collections.repositories

import io.webcontify.backend.JooqTestSetup
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
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
}
