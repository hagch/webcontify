package io.webcontify.backend.collections.services

import helpers.suppliers.collectionWithFields
import helpers.suppliers.firstSqlInsertedField
import helpers.suppliers.secondSqlInsertedField
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.webcontify.backend.collections.repositories.CollectionFieldRepository
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableFieldRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class CollectionFieldServiceTest {

  @RelaxedMockK lateinit var collectionFieldRepository: CollectionFieldRepository

  @RelaxedMockK lateinit var collectionTableFieldRepository: CollectionTableFieldRepository

  @RelaxedMockK lateinit var collectionRepository: CollectionRepository

  @InjectMockKs @SpyK lateinit var service: CollectionFieldService

  @Test
  fun `(getAll) should call collection field repository`() {
    service.getAllForCollection(0)

    verify(exactly = 1) { collectionFieldRepository.getAllForCollection(0) }
  }

  @Test
  fun `(getByCollectionIdAndName) should call collection field repository`() {
    service.getById(0, "test")

    verify(exactly = 1) { collectionFieldRepository.getByCollectionIdAndName(0, "test") }
  }

  @Test
  fun `(deleteById) should delete field and on table`() {
    val collection = collectionWithFields(listOf(Pair("test", false)))
    every { collectionRepository.getById(0) } returns collection

    service.deleteById(0, "test")

    verify(exactly = 1) {
      collectionFieldRepository.deleteById(0, "test")
      collectionTableFieldRepository.delete(collection, "test")
    }
  }

  @Test
  fun `(create) should create field and add on table`() {
    every { collectionFieldRepository.create(firstSqlInsertedField()) } returns
        firstSqlInsertedField()

    service.create(firstSqlInsertedField())

    verify(exactly = 1) {
      collectionFieldRepository.create(firstSqlInsertedField())
      collectionTableFieldRepository.create(any(), firstSqlInsertedField())
    }
  }

  @Test
  fun `(create) collection should call create for each field`() {
    every { service.create(any()) } returns firstSqlInsertedField()

    service.createForCollection(0, listOf(firstSqlInsertedField(), secondSqlInsertedField()))

    verify(exactly = 2) { service.create(any()) }
  }

  @Test
  fun `(create) for collection should not call create on field list is null`() {
    service.createForCollection(0, null)

    verify(exactly = 0) { service.create(any()) }
  }

  @Test
  fun `(create) for collection should not call create on field list is empty`() {
    service.createForCollection(0, listOf())

    verify(exactly = 0) { service.create(any()) }
  }

  @Test
  fun `(update) should update field and on table`() {
    every { collectionFieldRepository.update(firstSqlInsertedField(), "") } returns
        firstSqlInsertedField()

    service.update("", firstSqlInsertedField())

    verify(exactly = 1) {
      collectionFieldRepository.update(firstSqlInsertedField(), "")
      collectionTableFieldRepository.update(any(), firstSqlInsertedField(), "")
    }
  }
}
