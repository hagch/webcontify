package io.webcontify.backend.collections.services

import helpers.suppliers.collectionWithColumns
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
  fun `(getAll) should call collection column repository`() {
    service.getAllForCollection(0)

    verify(exactly = 1) { collectionColumnRepository.getAllForCollection(0) }
  }

  @Test
  fun `(getById) should call collection column repository`() {
    service.getById(0, "test")

    verify(exactly = 1) { collectionColumnRepository.getById(0, "test") }
  }

  @Test
  fun `(deleteById) should delete column and on table`() {
    val collection = collectionWithColumns(listOf(Pair("test", false)))
    every { collectionRepository.getById(0) } returns collection

    service.deleteById(0, "test")

    verify(exactly = 1) {
      collectionColumnRepository.deleteById(0, "test")
      collectionTableColumnRepository.delete(collection, "test")
    }
  }

  @Test
  fun `(create) should create column and add on table`() {
    every { collectionColumnRepository.create(firstSqlInsertedColumn()) } returns
        firstSqlInsertedColumn()

    service.create(firstSqlInsertedColumn())

    verify(exactly = 1) {
      collectionColumnRepository.create(firstSqlInsertedColumn())
      collectionTableColumnRepository.create(any(), firstSqlInsertedColumn())
    }
  }

  @Test
  fun `(create) collection should call create for each column`() {
    every { service.create(any()) } returns firstSqlInsertedColumn()

    service.createForCollection(0, listOf(firstSqlInsertedColumn(), secondSqlInsertedColumn()))

    verify(exactly = 2) { service.create(any()) }
  }

  @Test
  fun `(create) for collection should not call create on column list is null`() {
    service.createForCollection(0, null)

    verify(exactly = 0) { service.create(any()) }
  }

  @Test
  fun `(create) for collection should not call create on column list is empty`() {
    service.createForCollection(0, listOf())

    verify(exactly = 0) { service.create(any()) }
  }

  @Test
  fun `(update) should update column and on table`() {
    every { collectionColumnRepository.update(firstSqlInsertedColumn(), "") } returns
        firstSqlInsertedColumn()

    service.update("", firstSqlInsertedColumn())

    verify(exactly = 1) {
      collectionColumnRepository.update(firstSqlInsertedColumn(), "")
      collectionTableColumnRepository.update(any(), firstSqlInsertedColumn(), "")
    }
  }
}
