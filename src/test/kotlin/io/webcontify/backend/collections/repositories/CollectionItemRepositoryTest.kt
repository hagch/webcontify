package io.webcontify.backend.collections.repositories

import helpers.setups.repository.JooqTestSetup
import helpers.suppliers.collectionWithFields
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
  fun `(create) item should create item`() {
    val collection = collectionRepository.getAll().first()

    repository.create(collection, mapOf(Pair("id", 1), Pair("otherField", 1)))

    assertEquals(1, context.select().from(collection.name).where(field("id").eq(1)).execute())
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun `(create) item should throw exception on key in object does not exist`() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> {
      repository.create(collection, mapOf(Pair("notExists", 1)))
    }
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun `(create) item should throw exception on value is not supported on field`() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> {
      repository.create(collection, mapOf(Pair("notExists", "1")))
    }
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-fields.sql")
  fun `(create) item should throw error if table does not exist`() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> {
      repository.create(collection, mapOf(Pair("id", 1), Pair("otherField", 1)))
    }
  }

  @Test
  @Sql("/cleanup.sql", "get-items-test-entities.sql")
  fun `(getAll) for should return entities`() {
    val collection = collectionRepository.getAll().first()

    assertEquals(2, repository.getAllFor(collection).size)
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-fields.sql")
  fun `(getAll) for should throw error if table does not exist`() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> { repository.getAllFor(collection) }
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun `(getAll) for should return empty list`() {
    val collection = collectionRepository.getAll().first()

    assertEquals(0, repository.getAllFor(collection).size)
  }

  @Test
  @Sql("/cleanup.sql", "get-items-test-entities.sql")
  fun `(getByIdFor) should return item`() {
    val collection = collectionRepository.getAll().first()

    val item = repository.getByIdFor(collection, mapOf(Pair("id", 1)))

    assertEquals(1L, item["id"])
    assertEquals(1L, item["id"])
  }

  @Test
  @Sql("/cleanup.sql", "collection-with-fields.sql")
  fun `(getByIdFor) should throw exception if table does not exist`() {
    val collection = collectionRepository.getAll().first()

    assertThrows<UnprocessableContentException> {
      repository.getByIdFor(collection, mapOf(Pair("Name", 1)))
    }
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun `(getByIdFor) should throw exception if item does not exist`() {
    val collection = collectionRepository.getAll().first()

    assertThrows<NotFoundException> { repository.getByIdFor(collection, mapOf(Pair("id", 1))) }
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun `(deleteById) should delete item`() {
    val collection = collectionRepository.getAll().first()

    repository.deleteById(collection, mapOf(Pair("id", 1)))

    assertEquals(0, context.selectFrom(collection.name).where(field("id").eq(1)).execute())
  }

  @Test
  @Sql("/cleanup.sql", "create-item-test-entities.sql")
  fun `(deleteById) should not throw exception if item does not exist`() {
    val collection = collectionRepository.getAll().first()
    repository.deleteById(collection, mapOf(Pair("id", 1)))

    assertEquals(0, context.selectFrom(collection.name).where(field("id").eq(1)).execute())
    assertDoesNotThrow { repository.deleteById(collection, mapOf(Pair("id", 1))) }
  }

  @Test
  fun `(deleteById) should throw exception if table does not exist`() {
    val collection = collectionWithFields(listOf(Pair("id", true)))

    assertThrows<UnprocessableContentException> {
      repository.deleteById(collection, mapOf(Pair("id", 1)))
    }
  }
}
