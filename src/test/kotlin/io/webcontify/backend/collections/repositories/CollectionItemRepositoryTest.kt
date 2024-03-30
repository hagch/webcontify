package io.webcontify.backend.collections.repositories

import helpers.setups.repository.JooqTestSetup
import helpers.suppliers.collectionWithColumns
import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql

class CollectionItemRepositoryTest(
    @Autowired val context: DSLContext,
    @Autowired val repository: CollectionItemRepository,
    @Autowired val collectionRepository: CollectionRepository
) : JooqTestSetup() {

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun createItemShouldCreateItem() {
    val collection = collectionRepository.getAll().first()

    repository.create(collection, mapOf(Pair("id", 1), Pair("otherColumn", 1)))

    assertEquals(1, context.select().from(collection.name).where(field("id").eq(1)).execute())
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun createItemShouldThrowExceptionOnKeyInObjectDoesNotExist() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> {
      repository.create(collection, mapOf(Pair("notExists", 1)))
    }
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun createItemShouldThrowExceptionOnValueIsNotSupportedOnColumn() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> {
      repository.create(collection, mapOf(Pair("notExists", "1")))
    }
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun createItemShouldThrowAlreadyExistsIfPrimaryKeyIsAlreadyCreated() {
    val collection = collectionRepository.getAll().first()
    repository.create(collection, mapOf(Pair("id", 1), Pair("otherColumn", 1)))

    assertThrows<AlreadyExistsException> {
      repository.create(collection, mapOf(Pair("id", 1), Pair("otherColumn", 1)))
    }
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-columns.sql")
  fun createItemShouldThrowErrorIfTableDoesNotExist() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> {
      repository.create(collection, mapOf(Pair("id", 1), Pair("otherColumn", 1)))
    }
  }

  @Test
  @Sql("/cleanup.sql", "get-items-test-entities.sql")
  fun getAllForShouldReturnEntities() {
    val collection = collectionRepository.getAll().first()

    assertEquals(2, repository.getAllFor(collection).size)
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-columns.sql")
  fun getAllForShouldThrowErrorIfTableDoesNotExist() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> { repository.getAllFor(collection) }
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun getAllForShouldReturnEmptyList() {
    val collection = collectionRepository.getAll().first()

    assertEquals(0, repository.getAllFor(collection).size)
  }

  @Test
  @Sql("/cleanup.sql", "get-items-test-entities.sql")
  fun getByIdForShouldReturnItem() {
    val collection = collectionRepository.getAll().first()

    val item = repository.getByIdFor(collection, mapOf(Pair("id", 1)))

    assertEquals(1L, item["id"])
    assertEquals(1L, item["id"])
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-columns.sql")
  fun getByIdForShouldThrowExceptionIfTableDoesNotExist() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> {
      repository.getByIdFor(collection, mapOf(Pair("Name", 1)))
    }
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun getByIdForShouldThrowExceptionIfItemDoesNotExist() {
    val collection = collectionRepository.getAll().first()

    assertThrows<NotFoundException> { repository.getByIdFor(collection, mapOf(Pair("id", 1))) }
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun deleteByIdShouldDeleteItem() {
    val collection = collectionRepository.getAll().first()

    repository.deleteById(collection, mapOf(Pair("id", 1)))

    assertEquals(0, context.selectFrom(collection.name).where(field("id").eq(1)).execute())
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun deleteByIdShouldNotThrowExceptionIfItemDoesNotExist() {
    val collection = collectionRepository.getAll().first()
    repository.deleteById(collection, mapOf(Pair("id", 1)))

    assertEquals(0, context.selectFrom(collection.name).where(field("id").eq(1)).execute())
    assertDoesNotThrow { repository.deleteById(collection, mapOf(Pair("id", 1))) }
  }

  @Test
  fun deleteByIdShouldThrowExceptionIfTableDoesNotExist() {
    val collection = collectionWithColumns(listOf(Pair("id", true)))

    assertThrows<UnprocessableContentException> {
      repository.deleteById(collection, mapOf(Pair("id", 1)))
    }
  }
}
