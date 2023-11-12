package io.webcontify.backend.collections.repositories

import helpers.setups.JooqTestSetup
import helpers.suppliers.collectionWithColumns
import io.webcontify.backend.collections.exceptions.AlreadyExistsException
import io.webcontify.backend.collections.exceptions.NotFoundException
import io.webcontify.backend.collections.exceptions.UnprocessableContentException
import io.webcontify.backend.collections.utils.doubleQuote
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
  @Sql("create-item-test-entities.sql")
  fun createItemShouldCreateItem() {
    val collection = collectionRepository.getAll().first()

    repository.create(collection, mapOf(Pair("primary", 1), Pair("otherColumn", 1)))

    assertEquals(
        1,
        context
            .select()
            .from(collection.name.doubleQuote())
            .where(field("PRIMARY".doubleQuote()).eq(1))
            .execute())
  }

  @Test
  @Sql("create-item-test-entities.sql")
  fun createItemShouldThrowExceptionOnKeyInObjectDoesNotExist() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> {
      repository.create(collection, mapOf(Pair("notExists", 1)))
    }
  }

  @Test
  @Sql("create-item-test-entities.sql")
  fun createItemShouldThrowExceptionOnValueIsNotSupportedOnColumn() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> {
      repository.create(collection, mapOf(Pair("notExists", "1")))
    }
  }

  @Test
  @Sql("create-item-test-entities.sql")
  fun createItemShouldThrowAlreadyExistsIfPrimaryKeyIsAlreadyCreated() {
    val collection = collectionRepository.getAll().first()
    repository.create(collection, mapOf(Pair("primary", 1), Pair("otherColumn", 1)))

    assertThrows<AlreadyExistsException> {
      repository.create(collection, mapOf(Pair("primary", 1), Pair("otherColumn", 1)))
    }
  }

  @Test
  @Sql("collection-with-columns.sql")
  fun createItemShouldThrowErrorIfTableDoesNotExist() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> {
      repository.create(collection, mapOf(Pair("primary", 1), Pair("otherColumn", 1)))
    }
  }

  @Test
  @Sql("get-items-test-entities.sql")
  fun getAllForShouldReturnEntities() {
    val collection = collectionRepository.getAll().first()

    assertEquals(2, repository.getAllFor(collection).size)
  }

  @Test
  @Sql("collection-with-columns.sql")
  fun getAllForShouldThrowErrorIfTableDoesNotExist() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> { repository.getAllFor(collection) }
  }

  @Test
  @Sql("create-item-test-entities.sql")
  fun getAllForShouldReturnEmptyList() {
    val collection = collectionRepository.getAll().first()

    assertEquals(0, repository.getAllFor(collection).size)
  }

  @Test
  @Sql("get-items-test-entities.sql")
  fun getByIdForShouldReturnItem() {
    val collection = collectionRepository.getAll().first()

    val item = repository.getByIdFor(collection, mapOf(Pair("primary", 1)))

    assertEquals(1L, item["primary"])
    assertEquals(1L, item["primary"])
  }

  @Test
  @Sql("collection-with-columns.sql")
  fun getByIdForShouldThrowExceptionIfTableDoesNotExist() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> {
      repository.getByIdFor(collection, mapOf(Pair("Name", 1)))
    }
  }

  @Test
  @Sql("create-item-test-entities.sql")
  fun getByIdForShouldThrowExceptionIfItemDoesNotExist() {
    val collection = collectionRepository.getAll().first()

    assertThrows<NotFoundException> { repository.getByIdFor(collection, mapOf(Pair("primary", 1))) }
  }

  @Test
  @Sql("create-item-test-entities.sql")
  fun deleteByIdShouldDeleteItem() {
    val collection = collectionRepository.getAll().first()

    repository.deleteById(collection, mapOf(Pair("primary", 1)))

    assertEquals(
        0,
        context
            .selectFrom(collection.name.doubleQuote())
            .where(field("PRIMARY".doubleQuote()).eq(1))
            .execute())
  }

  @Test
  @Sql("create-item-test-entities.sql")
  fun deleteByIdShouldNotThrowExceptionIfItemDoesNotExist() {
    val collection = collectionRepository.getAll().first()
    repository.deleteById(collection, mapOf(Pair("primary", 1)))

    assertEquals(
        0,
        context
            .selectFrom(collection.name.doubleQuote())
            .where(field("PRIMARY".doubleQuote()).eq(1))
            .execute())
    assertDoesNotThrow { repository.deleteById(collection, mapOf(Pair("primary", 1))) }
  }

  @Test
  fun deleteByIdShouldThrowExceptionIfTableDoesNotExist() {
    val collection = collectionWithColumns(listOf(Pair("PRIMARY", true)))

    assertThrows<UnprocessableContentException> {
      repository.deleteById(collection, mapOf(Pair("primary", 1)))
    }
  }
}
