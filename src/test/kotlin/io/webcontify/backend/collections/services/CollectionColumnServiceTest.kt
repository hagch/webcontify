package io.webcontify.backend.collections.services

import helpers.suppliers.collectionWithEmptyColumns
import helpers.suppliers.firstSqlInsertedColumn
import helpers.suppliers.secondSqlInsertedColumn
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.webcontify.backend.collections.repositories.CollectionColumnRepository
import io.webcontify.backend.collections.repositories.CollectionRepository
import io.webcontify.backend.collections.repositories.CollectionTableColumnRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class CollectionColumnServiceTest {

  @RelaxedMockK lateinit var collectionColumnRepository: CollectionColumnRepository

  @RelaxedMockK lateinit var collectionTableColumnRepository: CollectionTableColumnRepository

  @RelaxedMockK lateinit var collectionRepository: CollectionRepository

  @InjectMockKs @SpyK lateinit var service: CollectionColumnService

  @Test
  fun getAllShouldCallCollectionColumnRepository() {
    service.getAllForCollection(0)

    verify(exactly = 1) { collectionColumnRepository.getAllForCollection(0) }
  }

  @Test
  fun getByIdShouldCallCollectionColumnRepository() {
    service.getById(0, "test")

    verify(exactly = 1) { collectionColumnRepository.getById(0, "test") }
  }

  @Test
  fun deleteByIdShouldDeleteColumnAndOnTable() {
    every { collectionRepository.getById(0) } returns collectionWithEmptyColumns()

    service.deleteById(0, "test")

    verify(exactly = 1) {
      collectionColumnRepository.deleteById(0, "test")
      collectionTableColumnRepository.delete(collectionWithEmptyColumns(), "test")
    }
  }

  @Test
  fun createShouldCreateColumnAndAddOnTable() {
    every { collectionColumnRepository.create(firstSqlInsertedColumn()) } returns
        firstSqlInsertedColumn()

    service.create(firstSqlInsertedColumn())

    verify(exactly = 1) {
      collectionColumnRepository.create(firstSqlInsertedColumn())
      collectionTableColumnRepository.create(any(), firstSqlInsertedColumn())
    }
  }

  @Test
  fun createForCollectionShouldCallCreateForEachColumn() {
    every { service.create(any()) } returns firstSqlInsertedColumn()

    service.createForCollection(0, listOf(firstSqlInsertedColumn(), secondSqlInsertedColumn()))

    verify(exactly = 2) { service.create(any()) }
  }

  @Test
  fun createForCollectionShouldNotCallCreateOnColumnListIsNull() {
    service.createForCollection(0, null)

    verify(exactly = 0) { service.create(any()) }
  }

  @Test
  fun createForCollectionShouldNotCallCreateOnColumnListIsEmpty() {
    service.createForCollection(0, listOf())

    verify(exactly = 0) { service.create(any()) }
  }

  @Test
  fun updateShouldUpdateColumnAndOnTable() {
    every { collectionColumnRepository.update(firstSqlInsertedColumn(), "") } returns
        firstSqlInsertedColumn()

    service.update("", firstSqlInsertedColumn())

    verify(exactly = 1) {
      collectionColumnRepository.update(firstSqlInsertedColumn(), "")
      collectionTableColumnRepository.update(any(), firstSqlInsertedColumn(), "")
    }
  }
}
